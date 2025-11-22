package me.aap.fermata.whisper;

import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.addon.SubGenAddon;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

public final class Whisper implements SubGenAddon.Transcriptor {
	private static final String VAD_FILE_NAME = "ggml-silero-v5.1.2.bin";
	private static final Map<String, String> NAME_TO_URL = new LinkedHashMap<>();
	private static final List<Pair<String, String>> displayNames;

	static {
		System.loadLibrary("whisper_jni");

		Map<String, String> nameToUrl = new LinkedHashMap<>();
		nameToUrl.put("tiny-q5_1 (32.2 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin");
		nameToUrl.put("tiny-q8_0 (43.5 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin");
		nameToUrl.put("tiny (77.7 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin");
		nameToUrl.put("tiny.en-q5_1 (32.2 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin");
		nameToUrl.put("tiny.en-q8_0 (43.6 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q8_0.bin");
		nameToUrl.put("tiny.en (77.7 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin");
		nameToUrl.put("base-q5_1 (59.7 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin");
		nameToUrl.put("base-q8_0 (81.8 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin");
		nameToUrl.put("base (148 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin");
		nameToUrl.put("base.en-q5_1 (59.7 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin");
		nameToUrl.put("base.en-q8_0 (81.8 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q8_0.bin");
		nameToUrl.put("base.en (148 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin");
		nameToUrl.put("small-q5_1 (190 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin");
		nameToUrl.put("small-q8_0 (264 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin");
		nameToUrl.put("small (488 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin");
		nameToUrl.put("small.en-q5_1 (190 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin");
		nameToUrl.put("small.en-q8_0 (264 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q8_0.bin");
		nameToUrl.put("small.en (488 MB)",
				"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin");
		displayNames = new ArrayList<>(nameToUrl.size());

		for (var entry : nameToUrl.entrySet()) {
			var name = entry.getKey();
			var key = name.substring(0, name.indexOf('(') - 1);
			displayNames.add(new Pair<>(key, name));
			NAME_TO_URL.put(key, entry.getValue());
		}
	}

	private final long sessionPtr;
	private boolean released = false;

	private Whisper(long sessionPtr) {
		this.sessionPtr = sessionPtr;
		Log.d("Session created: ", sessionPtr);
	}

	static List<Pair<String, String>> getModelNames() {
		return displayNames;
	}

	public static FutureSupplier<Whisper> create(PreferenceStore ps) {
		var modelNameOrPath = ps.getStringPref(WhisperAddon.MODEL);
		var lang = ps.getStringPref(WhisperAddon.LANG);
		var singleSegment = ps.getBooleanPref(WhisperAddon.SINGLE_SEGMENT);
		var app = App.get();
		var cacheDir = cacheDir(app);
		var vadFile = new File(cacheDir, VAD_FILE_NAME);
		String modelPath;
		FutureSupplier<?> load;

		if (modelNameOrPath.indexOf('/') < 0) {
			var url = NAME_TO_URL.get(modelNameOrPath);
			if (url == null) throw new IllegalArgumentException("Unknown model name: " + modelNameOrPath);
			var file = new File(cacheDir, FileUtils.getFileName(url));
			modelPath = file.getAbsolutePath();
			if (file.isFile()) {
				load = completedVoid();
			} else {
				load = Utils.createDownloader(app, url).download(url, file);
			}
		} else if (new File(modelNameOrPath).isFile()) {
			modelPath = modelNameOrPath;
			load = completedVoid();
		} else {
			throw new IllegalArgumentException("Invalid model path: " + modelNameOrPath);
		}

		if (!vadFile.isFile()) {
			Runnable copy = () -> {
				Log.i("Extracting VAD model ", VAD_FILE_NAME);
				var tmp = new File(vadFile.getAbsolutePath() + ".tmp");
				try (var is = app.getAssets().open(VAD_FILE_NAME);
						 var os = new FileOutputStream(tmp)) {
					FileUtils.copy(is, os);
					FileUtils.move(tmp, vadFile);
				} catch (IOException err) {
					throw new RuntimeException("Failed to extract asset " + VAD_FILE_NAME, err);
				}
			};
			load = load.isDoneNotFailed() ? app.getExecutor().submit(copy) : load.thenRun(copy);
		}

		return load.map(
				v -> new Whisper(create(modelPath, vadFile.getAbsolutePath(), lang, singleSegment)));
	}

	@Override
	public boolean reconfigure(PreferenceStore ps) {
		reconfigure(sessionPtr, ps.getStringPref(WhisperAddon.LANG),
				ps.getBooleanPref(WhisperAddon.SINGLE_SEGMENT));
		return true;
	}

	@Override
	public boolean read(ByteBuffer buf, int chunkLen, int bytesPerSample, int channels,
											int frameRate) {
		assert buf.isDirect();
		var slice = buf.position() == 0 && buf.limit() == buf.capacity() ? buf : buf.slice();
		int consumed = resample(sessionPtr, slice, chunkLen, bytesPerSample, channels, frameRate);
		buf.position(buf.position() + Math.abs(consumed));
		return consumed <= 0;
	}

	@Override
	public List<Subtitles.Text> transcribe(long timeOffset) {
		Log.d("Transcribing...");
		int segments = fullTranscribe(sessionPtr);
		Log.d("Number of segments: ", segments);
		if (segments == 0) return emptyList();

		var subtitles = new ArrayList<Subtitles.Text>(segments);
		for (int i = 0; i < segments; i++) {
			String text = text(sessionPtr, i).trim();
			long start = start(sessionPtr, i);
			long end = end(sessionPtr, i);
			var t = new Subtitles.Text(text, timeOffset + start, (int) (end - start));
			Log.d(t);
			subtitles.add(t);
		}
		return subtitles;
	}

	@Override
	public String getLang() {
		return lang(sessionPtr);
	}

	public void reset() {
		reset(sessionPtr);
	}

	@Override
	public synchronized void release() {
		if (!released) {
			released = true;
			release(sessionPtr);
			Log.d("Session released: ", sessionPtr);
		}
	}

	@Override
	protected void finalize() {
		release();
	}

	private static File cacheDir(Context ctx) {
		return new File(ctx.getCacheDir(), "whisper");
	}

	static Collection<String> cleanUp(Context ctx, PreferenceStore ps) {
		var cacheDir = cacheDir(ctx);
		var ls = cacheDir.list();
		if (ls == null || ls.length == 0) return Collections.emptyList();
		var deleted = new ArrayList<String>(ls.length);
		var current = NAME_TO_URL.get(ps.getStringPref(WhisperAddon.MODEL));
		if (current != null) current = FileUtils.getFileName(current);
		Log.d("Cleaning up whisper cache, current model: ", current);
		for (String name : ls) {
			if (name.equals(VAD_FILE_NAME) || name.equals(current)) continue;
			var f = new File(cacheDir, name);
			if (f.delete()) {
				deleted.add(name);
			} else {
				Log.w("Failed to delete file from cache: ", f);
			}
		}
		return deleted;
	}

	private static native long create(String modelPath, String vadPath, String lang,
																		boolean singleSegment);

	private static native void reconfigure(long sessionPtr, String lang, boolean singleSegment);

	private static native int resample(long sessionPtr, ByteBuffer buf, int chunkLen,
																		 int bytesPerSample, int channels, int frameRate);

	private static native int fullTranscribe(long sessionPtr);

	private static native String text(long sessionPtr, int segmentIdx);

	private static native long start(long sessionPtr, int segmentIdx);

	private static native long end(long sessionPtr, int segmentIdx);

	private static native String lang(long sessionPtr);

	private static native void reset(long sessionPtr);

	private static native void release(long sessionPtr);
}

