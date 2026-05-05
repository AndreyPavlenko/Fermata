#include <cassert>
#include <jni.h>
#include <map>
#include <string>
#include <thread>

#include "android/log.h"
#include "ggml.h"
#include "whisper.h"

#define  LOG_TAG "[me.aap.fermata.Whisper]"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#ifndef NDEBUG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) \
    do {           \
      /* no debug logging */ \
    } while (0)
#endif

static constexpr unsigned OVERLAP_SAMPLES = WHISPER_SAMPLE_RATE * 200 / 1000;
static constexpr unsigned SAMPLES_CAPACITY = WHISPER_SAMPLE_RATE * 180;

struct WhisperSession {
	explicit WhisperSession(struct whisper_context *ctx, std::string &vadPath, std::string &lang)
			: ctx(ctx), vadPath(vadPath), lang(lang),
				params(whisper_full_default_params(WHISPER_SAMPLING_GREEDY)) {
		reset();
		params.n_threads = static_cast<int>(std::thread::hardware_concurrency());
		params.print_special = false;
		params.print_progress = false;
		params.print_realtime = false;
		params.print_timestamps = false;
		params.max_len = 1;
		params.split_on_word = true;

		if (!this->vadPath.empty()) {
			params.vad = true;
			params.vad_model_path = this->vadPath.c_str();
		}
	}

	void reset() {
		size = 0;
		consumed = 0;
		timestampOffset = 0;
		curSkip = 0;
		overlapSize = 0;
		whisper_reset_timings(ctx);
		params.language = lang.empty() ? nullptr : lang.c_str();
		params.detect_language = lang.empty() || lang == "auto";
	}

	struct whisper_context *ctx;
	std::string vadPath;
	std::string lang;
	whisper_full_params params;
	size_t consumed{0};
	size_t timestampOffset{0};
	unsigned size{0};
	int curSkip{0};
	unsigned overlapSize{0};
	float samples[SAMPLES_CAPACITY]{};
};

template<unsigned S>
using SampleType = std::conditional_t<
		S == 1,
		int8_t,
		std::conditional_t<S == 3, uint8_t, std::conditional_t<S == 2, int16_t, int32_t>>
>;

template<unsigned S, typename T = SampleType<S>>
struct SampleBuffer {
	SampleBuffer(JNIEnv *env, jobject byteBuf) :
			data(static_cast<T *>(env->GetDirectBufferAddress(byteBuf))),
			size(static_cast<size_t>(env->GetDirectBufferCapacity(byteBuf) / S)) {
		static_assert(S >= 1 && S <= 4, "Unsupported sample size");
	}

	T *data;
	size_t size;

	// Convert a single sample at idx to a whisper sample - float in range [-1.0, 1.0)
	float operator[](size_t idx) {
		constexpr auto div = static_cast<float>(1ULL << (8 * S - 1));
		if constexpr (S == 3) {
			const uint8_t *p = data + idx * 3;
			int32_t v = p[0] | (p[1] << 8) | (p[2] << 16);
			// Extend the sign bit from 24th to 32nd bit
			constexpr int32_t sign_bit = 1 << 23;
			return static_cast<float>((v ^ sign_bit) - sign_bit) / div;
		} else {
			return static_cast<float>(data[idx]) / div;
		}
	}

	static size_t
	copy(JNIEnv *env, jobject src, WhisperSession *dst, unsigned channels, size_t frameRate) {
		SampleBuffer<S> buf(env, src);
		size_t n = buf.size * WHISPER_SAMPLE_RATE / channels / frameRate;
		size_t available = SAMPLES_CAPACITY - dst->size;
		if (n > available) {
			LOGI("Sample buffer overflow: dropping %zu frames", n - available);
			n = available;
		}
		auto samples = dst->samples + dst->size;
		size_t consumed;
		if ((channels * frameRate) % WHISPER_SAMPLE_RATE) {
			float ratio =
					static_cast<float>(channels * frameRate) / static_cast<float>(WHISPER_SAMPLE_RATE);
			size_t frameWidth = std::max(static_cast<size_t>(1),
																	 static_cast<size_t>(channels * frameRate / WHISPER_SAMPLE_RATE));
			for (size_t i = 0; i < n; ++i) {
				auto frameIdx = static_cast<size_t>(static_cast<float>(i) * ratio);
				float sum = 0.0f;
				for (size_t j = 0; j < frameWidth; ++j) sum += buf[frameIdx + j];
				samples[i] = sum / static_cast<float>(frameWidth);
			}
			consumed = static_cast<size_t>(static_cast<float>(n) * ratio) * S;
		} else {
			size_t ratio = (channels * frameRate) / WHISPER_SAMPLE_RATE;
			size_t frameWidth = std::max(static_cast<size_t>(1), ratio);
			for (size_t i = 0; i < n; ++i) {
				auto frameIdx = i * ratio;
				float sum = 0.0f;
				for (size_t j = 0; j < frameWidth; ++j) sum += buf[frameIdx + j];
				samples[i] = sum / static_cast<float>(frameWidth);
			}
			consumed = n * ratio * S;
		}
		dst->size += n;
		return consumed;
	}
};

template<unsigned C, size_t R>
using SampleRatioType = std::conditional_t<((C * R) % WHISPER_SAMPLE_RATE) == 0, size_t, float>;

template<unsigned S, unsigned C, size_t R, typename Srt = SampleRatioType<C, R>,
		Srt Ratio = static_cast<Srt>(C * R) / static_cast<Srt>(WHISPER_SAMPLE_RATE)>
struct FrameBuffer {
	FrameBuffer(JNIEnv *env, jobject byteBuf)
			: buf(env, byteBuf) {
		static_assert(C >= 1, "The number of channels must be >= 1");
		static_assert(R > 0, "Frame rate must be > 0");
	}

	SampleBuffer<S> buf;

	size_t size() {
		return static_cast<size_t>(static_cast<Srt>(buf.size) / Ratio);
	}

	// Convert a single frame at idx to a whisper sample, scaling the frame rate R proportionally
	float operator[](size_t idx) {
		if constexpr (std::is_same_v<Srt, size_t>) {
			if constexpr (Ratio == 1) { // 16kHz mono
				return buf[idx];
			} else if constexpr (Ratio == 2) { // 16kHz stereo
				idx *= 2;
				return 0.5f * (buf[idx] + buf[idx + 1]);
			} else if constexpr (Ratio == 3) { // 48kHz mono
				return buf[idx * 3 + 1]; // Take the middle sample
			} else if constexpr (Ratio == 6) { // 48kHz stereo
				idx *= 6;
				return 0.5f * (buf[idx + 2] + buf[idx + 3]); // Take mid of L and R
			}
		} else if constexpr (R == 44100) {
			idx = static_cast<size_t>(static_cast<Srt>(idx) * Ratio);
			if constexpr (C == 1) { // 44.1kHz mono
				return buf[idx + 1];
			} else { // 44.1kHz stereo
				return 0.5f * (buf[idx + 2] + buf[idx + 3]);
			}
		}

		constexpr auto frameWidth = static_cast<size_t>(Ratio) == 0 ? 1 : static_cast<size_t>(Ratio);
		idx = static_cast<size_t>(static_cast<Srt>(idx) * Ratio);
		float sum = 0.0f;
		for (size_t i = 0; i < frameWidth; ++i) {
			sum += buf[idx + i];
		}
		return sum / static_cast<float>(frameWidth);
	}

	static size_t copy(JNIEnv *env, jobject src, WhisperSession *dst) {
		FrameBuffer<S, C, R> buf(env, src);
		size_t available = SAMPLES_CAPACITY - dst->size;
		auto n = buf.size();
		if (n > available) {
			LOGI("Sample buffer overflow: dropping %zu frames", n - available);
			n = available;
		}
		auto samples = dst->samples + dst->size;
		for (size_t i = 0; i < n; ++i) {
			samples[i] = buf[i];
		}
		dst->size += n;
		auto nBytes = static_cast<size_t>(static_cast<Srt>(n) * Ratio * S);
		LOGD("Resampled %zu frames, consumed %zu: %u bytes per sample, %u channels, %zu Hz",
				 n, nBytes, S, C, R);
		return nBytes;
	}
};

extern "C" JNIEXPORT jlong JNICALL
Java_me_aap_fermata_whisper_Whisper_create(JNIEnv *env, jclass, jstring jModelPath,
																					 jstring jvadPath, jstring jlang,
																					 jboolean useGpu) {
	const char *modelPath = env->GetStringUTFChars(jModelPath, nullptr);
	whisper_context_params params = whisper_context_default_params();
	params.use_gpu = useGpu;
	params.flash_attn = false;
	struct whisper_context *ctx = whisper_init_from_file_with_params(modelPath, params);
	env->ReleaseStringUTFChars(jModelPath, modelPath);
	if (!ctx) {
		env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to load whisper model");
		return 0;
	}

	try {
		std::string lang;
		std::string vadPath;
		for (auto &[s, js]: {std::make_pair(&lang, jlang), std::make_pair(&vadPath, jvadPath)}) {
			if (js) {
				auto chars = env->GetStringUTFChars(js, nullptr);
				*s = chars;
				env->ReleaseStringUTFChars(js, chars);
			}
		}
		return reinterpret_cast<jlong>(new WhisperSession(ctx, vadPath, lang));
	} catch (const std::bad_alloc &) {
		whisper_free(ctx);
		env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"),
									"Failed to allocate WhisperSession");
		return 0;
	}
}

extern "C" JNIEXPORT void JNICALL
Java_me_aap_fermata_whisper_Whisper_reconfigure(JNIEnv *env, jclass, jlong sessionPtr,
																								jstring jlang) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	auto chars = env->GetStringUTFChars(jlang, nullptr);
	session->lang = chars;
	env->ReleaseStringUTFChars(jlang, chars);
	session->reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_me_aap_fermata_whisper_Whisper_resample(JNIEnv *env, jclass, jlong sessionPtr, jobject byteBuf,
																						 jint chunkLen, jint bytesPerSample, jint channels,
																						 jint frameRate) {
	assert(sessionPtr);
	static auto resamplers = std::map<std::tuple<jint, jint, jint>, size_t (*)(JNIEnv *, jobject,
																																						 WhisperSession *)>{
			{{1, 1, 16000}, &FrameBuffer<1, 1, 16000>::copy},
			{{1, 1, 44100}, &FrameBuffer<1, 1, 44100>::copy},
			{{1, 1, 48000}, &FrameBuffer<1, 1, 48000>::copy},
			{{2, 1, 16000}, &FrameBuffer<2, 1, 16000>::copy},
			{{2, 1, 44100}, &FrameBuffer<2, 1, 44100>::copy},
			{{2, 1, 48000}, &FrameBuffer<2, 1, 48000>::copy},
			{{2, 2, 16000}, &FrameBuffer<2, 2, 16000>::copy},
			{{2, 2, 44100}, &FrameBuffer<2, 2, 44100>::copy},
			{{2, 2, 48000}, &FrameBuffer<2, 2, 48000>::copy},
			{{3, 1, 16000}, &FrameBuffer<3, 1, 16000>::copy},
			{{3, 1, 44100}, &FrameBuffer<3, 1, 44100>::copy},
			{{3, 1, 48000}, &FrameBuffer<3, 1, 48000>::copy},
			{{3, 2, 16000}, &FrameBuffer<3, 2, 16000>::copy},
			{{3, 2, 44100}, &FrameBuffer<3, 2, 44100>::copy},
			{{3, 2, 48000}, &FrameBuffer<3, 2, 48000>::copy},
			{{4, 1, 16000}, &FrameBuffer<4, 1, 16000>::copy},
			{{4, 1, 44100}, &FrameBuffer<4, 1, 44100>::copy},
			{{4, 1, 48000}, &FrameBuffer<4, 1, 48000>::copy},
			{{4, 2, 16000}, &FrameBuffer<4, 2, 16000>::copy},
			{{4, 2, 44100}, &FrameBuffer<4, 2, 44100>::copy},
			{{4, 2, 48000}, &FrameBuffer<4, 2, 48000>::copy},
	};

	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	size_t consumed;

	if (auto it = resamplers.find({bytesPerSample, channels, frameRate}); it != resamplers.end()) {
		consumed = it->second(env, byteBuf, session);
	} else {
		size_t (*resampler)(JNIEnv *, jobject, WhisperSession *, uint, size_t);
		switch (bytesPerSample) {
			case 1:
				resampler = &SampleBuffer<1>::copy;
				break;
			case 2:
				resampler = &SampleBuffer<2>::copy;
				break;
			case 3:
				resampler = &SampleBuffer<3>::copy;
				break;
			case 4:
				resampler = &SampleBuffer<4>::copy;
				break;
			default:
				env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
											"Unsupported bytesPerSample");
				return 0;
		}
		consumed = resampler(env, byteBuf, session, static_cast<uint>(channels),
												 static_cast<size_t>(frameRate));
	}

	auto result = static_cast<jint>(consumed);
	// Negative value means there are enough samples for transcription
	return (session->size >= static_cast<unsigned>(chunkLen * WHISPER_SAMPLE_RATE)) ? -result
																																									: result;
}

extern "C" JNIEXPORT jint JNICALL
Java_me_aap_fermata_whisper_Whisper_fullTranscribe(JNIEnv *env, jclass, jlong sessionPtr) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	if (session->size == 0) return 0;

	whisper_full_params &params = session->params;
	int segments = 0;
	bool retry = false;
	do {
		retry = false;
		if (whisper_full(session->ctx, params, session->samples, static_cast<int>(session->size)) !=
				0) {
			env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to transcribe");
			return 0;
		}

		segments = whisper_full_n_segments(session->ctx);
		unsigned consumed = session->size;

		if (segments) { // Trim to a sentence boundary
			auto totalSegments = segments;
			auto dur = whisper_full_get_segment_t1(session->ctx, segments - 1) -
								 whisper_full_get_segment_t0(session->ctx, 0);
			for (decltype(dur) trimDur = 0; segments > 0; --segments) {
				auto seg = segments - 1;
				trimDur += whisper_full_get_segment_t1(session->ctx, seg) -
									 whisper_full_get_segment_t0(session->ctx, seg);
				if (trimDur >= 1000) break; // Do not trim more than 10 seconds

				const char *text = whisper_full_get_segment_text(session->ctx, seg);
				size_t textLen = text ? std::strlen(text) : 0;
				for (; textLen > 0 && std::isspace((unsigned char) text[textLen - 1]); --textLen);
				if (!textLen) continue;
				char c = text[textLen - 1];
				if (c == '.' || c == '?' || c == '!' ||
						((dur - trimDur > 300) && (c == ',' || c == ';' || c == ':' || c == '-'))) {
					break;
				}
			}

			if (segments != totalSegments) {
				unsigned end;
				if (segments > 0) {
					end = static_cast<unsigned>(whisper_full_get_segment_t1(session->ctx, segments - 1) *
																			WHISPER_SAMPLE_RATE / 100);
				} else {
					end = static_cast<unsigned>(whisper_full_get_segment_t0(session->ctx, 0) *
																			WHISPER_SAMPLE_RATE / 100);
				}
				consumed = std::min(session->size, end);
			}
		}

		if (params.detect_language) {
			params.language = whisper_lang_str(whisper_full_lang_id(session->ctx));
			if (params.language) {
				params.detect_language = false;
				session->lang = params.language;
				LOGI("Detected language: %s", params.language);
				if (segments == 0) {
					LOGD("No segments detected. Retrying after language detection.");
					retry = true;
					continue;
				}
			}
		}

		// Skip segments that fall entirely within the overlap region (already reported)
		auto overlapCs = static_cast<int64_t>(session->overlapSize) * 100 / WHISPER_SAMPLE_RATE;
		int skip = 0;
		for (; skip < segments; skip++) {
			if (whisper_full_get_segment_t0(session->ctx, skip) >= overlapCs) break;
		}
		session->curSkip = skip;

		// Keep overlap context at front of buffer for better boundary quality
		unsigned shiftFrom = consumed;
		unsigned newOverlap = 0;
		if (consumed < session->size && consumed > OVERLAP_SAMPLES) {
			shiftFrom = consumed - OVERLAP_SAMPLES;
			newOverlap = OVERLAP_SAMPLES;
		}

		if (shiftFrom >= session->size) {
			session->size = 0;
		} else {
			std::memmove(session->samples, session->samples + shiftFrom,
									 (session->size - shiftFrom) * sizeof(float));
			session->size -= shiftFrom;
		}

		session->timestampOffset = session->consumed * 1000 / WHISPER_SAMPLE_RATE;
		session->consumed += shiftFrom;
		session->overlapSize = newOverlap;
	} while (retry);

	int reported = segments - session->curSkip;
	return reported > 0 ? reported : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_aap_fermata_whisper_Whisper_text(JNIEnv *env, jclass, jlong sessionPtr, jint segmentIdx) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	const char *segTxt = whisper_full_get_segment_text(session->ctx, segmentIdx + session->curSkip);
	return env->NewStringUTF(segTxt ? segTxt : "");
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_aap_fermata_whisper_Whisper_start(JNIEnv *, jclass, jlong sessionPtr, jint segmentIdx) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	auto t = session->timestampOffset +
					 whisper_full_get_segment_t0(session->ctx, segmentIdx + session->curSkip) * 10;
	return static_cast<jlong>(t);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_aap_fermata_whisper_Whisper_end(JNIEnv *, jclass, jlong sessionPtr, jint segmentIdx) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	auto t = session->timestampOffset +
					 whisper_full_get_segment_t1(session->ctx, segmentIdx + session->curSkip) * 10;
	return static_cast<jlong>(t);
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_aap_fermata_whisper_Whisper_lang(JNIEnv *env, jclass, jlong sessionPtr) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	auto lang = session->params.language;
	if (lang == nullptr || std::strcmp("auto", lang) == 0) {
		lang = whisper_lang_str(whisper_full_lang_id(session->ctx));
	}
	return env->NewStringUTF(lang == nullptr ? "" : lang);
}

extern "C" JNIEXPORT void JNICALL
Java_me_aap_fermata_whisper_Whisper_reset(JNIEnv *, jclass, jlong sessionPtr) {
	assert(sessionPtr);
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	session->reset();
}

extern "C" JNIEXPORT void JNICALL
Java_me_aap_fermata_whisper_Whisper_release(JNIEnv *, jclass, jlong sessionPtr) {
	if (!sessionPtr) return;
	auto session = reinterpret_cast<WhisperSession *>(sessionPtr);
	whisper_free(session->ctx);
	delete session;
}