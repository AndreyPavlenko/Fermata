package me.aap.utils.voice;

import static android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH;
import static android.speech.RecognizerIntent.EXTRA_LANGUAGE;
import static android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL;
import static android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS;
import static android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM;
import static android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED;
import static android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE;
import static android.speech.SpeechRecognizer.ERROR_NO_MATCH;
import static android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_UNKNOWN;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpeechToText implements RecognitionListener, Closeable {
	@Nullable
	private SpeechRecognizer recognizer;
	private final Intent recognizerIntent;
	private Promise promise;
	private Result result;

	public SpeechToText(Context ctx) {
		this(ctx, null);
	}

	public SpeechToText(Context ctx, @Nullable Locale lang) {
		this(ctx, lang, null);
	}

	public SpeechToText(Context ctx, @Nullable Locale lang,
											@Nullable ComponentName serviceComponent) {
		ComponentName cn = null;

		if (serviceComponent != null) {
			Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
			for (ResolveInfo service : ctx.getPackageManager().queryIntentServices(intent, 0)) {
				if (serviceComponent.getPackageName().equals(service.serviceInfo.packageName) &&
						serviceComponent.getClassName().equals(service.serviceInfo.name)) {
					cn = serviceComponent;
					Log.i("Using the recognition service ", cn);
					break;
				}
			}
		}

		recognizer = SpeechRecognizer.createSpeechRecognizer(ctx, cn);
		recognizer.setRecognitionListener(this);
		recognizerIntent = new Intent(ACTION_RECOGNIZE_SPEECH);
		recognizerIntent.putExtra(EXTRA_PARTIAL_RESULTS, true);
		recognizerIntent.putExtra(EXTRA_LANGUAGE_MODEL, LANGUAGE_MODEL_FREE_FORM);
		if (lang != null) recognizerIntent.putExtra(EXTRA_LANGUAGE, lang.toLanguageTag());
	}

	public Intent getRecognizerIntent() {
		return recognizerIntent;
	}

	public <D> FutureSupplier<Result<D>> recognize(@Nullable D data) {
		if (recognizer == null) return cancelled();
		if (promise != null) promise.cancel();
		Promise<Result<D>> p = new Promise<>();
		promise = p;
		result = new Result(data);
		recognizer.startListening(recognizerIntent);
		return p;
	}

	public void stop() {
		if (promise != null) promise.cancel();
		if (recognizer != null) recognizer.cancel();
	}

	@Override
	public void close() {
		if (recognizer == null) return;
		if (promise != null) promise.cancel();
		promise = null;
		result = null;
		recognizer.destroy();
		recognizer = null;
	}


	@Override
	public void onResults(Bundle b) {
		if (promise == null) return;
		Promise p = promise;
		Result r = result;
		promise = null;
		result = null;
		var t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
		if ((t != null) && !t.isEmpty() && (t.get(0) != null)) r.text = t;
		p.complete(r);
	}

	@Override
	public void onPartialResults(Bundle b) {
		if (promise == null) return;
		var t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
		if ((t == null) || t.isEmpty() || (t.get(0) == null)) return;
		result.text = t;
		promise.setProgress(result, PROGRESS_UNKNOWN, PROGRESS_UNKNOWN);
	}

	@Override
	public void onEndOfSegmentedSession() {
		if (promise == null) return;
		Promise p = promise;
		Result r = result;
		promise = null;
		result = null;
		p.complete(r);
	}

	@Override
	public void onSegmentResults(@NonNull Bundle b) {
		onPartialResults(b);
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void onError(int error) {
		if (promise == null) return;

		switch (error) {
			case ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT -> {
				Promise p = promise;
				Result r = result;
				promise = null;
				result = null;
				r.text = Result.EMPTY;
				p.complete(r);
				return;
			}
			case ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
				if ((recognizer != null) && (VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE)) {
					Log.i(new SpeechToTextException(error).toString(), ". Trying to download.");
					recognizer.triggerModelDownload(recognizerIntent, App.get().getHandler(),
							new ModelDownloadListener() {
								@Override
								public void onProgress(int completedPercent) {
									Log.d("Model download progress: ", completedPercent);
								}

								@Override
								public void onSuccess() {
									Log.d("Model download completed");
									recognizer.startListening(recognizerIntent);
								}

								@Override
								public void onScheduled() {
									Log.d("Model download scheduled");
								}

								@Override
								public void onError(int err) {
									Log.d("Model download failed: ", err);
									Promise p = promise;
									promise = null;
									result = null;
									p.completeExceptionally(new SpeechToTextException(error));
								}
							});
					return;
				}
			}
		}

		Promise p = promise;
		promise = null;
		result = null;
		p.completeExceptionally(new SpeechToTextException(error));
	}

	@Override
	public void onReadyForSpeech(Bundle params) {
	}

	@Override
	public void onBeginningOfSpeech() {
	}

	@Override
	public void onRmsChanged(float rmsdB) {
	}

	@Override
	public void onBufferReceived(byte[] buffer) {
	}

	@Override
	public void onEndOfSpeech() {
	}

	@Override
	public void onEvent(int eventType, Bundle params) {
	}

	public static class Result<D> {
		static final List<String> EMPTY = Collections.singletonList(null);
		private final D data;
		@NonNull
		List<String> text = EMPTY;

		public Result(D data) {
			this.data = data;
		}

		public D getData() {
			return data;
		}

		@NonNull
		public List<String> getText() {
			return text;
		}
	}
}
