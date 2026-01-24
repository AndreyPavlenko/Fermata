package me.aap.utils.voice;

import static android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA;
import static android.speech.tts.TextToSpeech.LANG_MISSING_DATA;
import static android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED;
import static android.speech.tts.TextToSpeech.QUEUE_FLUSH;
import static android.speech.tts.TextToSpeech.SUCCESS;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.voice.TextToSpeechException.TTS_ERR_CLOSED;
import static me.aap.utils.voice.TextToSpeechException.TTS_ERR_INIT;
import static me.aap.utils.voice.TextToSpeechException.TTS_ERR_LANG_MISSING_DATA;
import static me.aap.utils.voice.TextToSpeechException.TTS_ERR_LANG_NOT_SUPPORTED;
import static me.aap.utils.voice.TextToSpeechException.TTS_ERR_SPEAK_FAILED;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;

import java.io.Closeable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TextToSpeech implements Closeable {
	@Nullable
	private android.speech.tts.TextToSpeech tts;
	private final Context ctx;
	private Promise promise;
	private String uId;
	private Object uData;
	private int uCounter;

	private TextToSpeech(Context ctx, String engine, Promise promise) {
		this.ctx = ctx;
		this.promise = promise;
		Listener l = new Listener();
		tts = new android.speech.tts.TextToSpeech(ctx, l, engine);
		tts.setOnUtteranceProgressListener(l);
	}

	public static FutureSupplier<TextToSpeech> create(Context ctx) {
		return create(ctx, null);
	}

	public static FutureSupplier<TextToSpeech> create(Context ctx, @Nullable Locale lang) {
		return create(ctx, lang, null);
	}

	public static FutureSupplier<TextToSpeech> create(Context ctx, @Nullable Locale lang,
																										@Nullable String engine) {
		Promise<TextToSpeech> p = new Promise<>();
		new TextToSpeech(ctx, engine, p);
		if (lang == null) return p;
		return p.map(t -> {
			t.setLanguage(lang);
			return t;
		});
	}

	/**
	 * @noinspection UnusedReturnValue
	 */
	public static FutureSupplier<Intent> installTtsData(Context ctx) {
		return ActivityDelegate.getActivityDelegate(ctx)
				.then(d -> d.startActivityForResult(() -> new Intent(ACTION_INSTALL_TTS_DATA)));
	}


	public Set<Locale> getAvailableLanguages() {
		return (tts == null) ? Collections.emptySet() : tts.getAvailableLanguages();
	}

	public void setLanguage(final Locale lang) throws TextToSpeechException {
		if (tts == null) throw new TextToSpeechException("TTS closed", TTS_ERR_CLOSED);
		switch (tts.setLanguage(lang)) {
			case LANG_MISSING_DATA -> {
				close();
				installTtsData(ctx);
				throw new TextToSpeechException("Missing TTS data for language " + lang,
						TTS_ERR_LANG_MISSING_DATA);
			}
			case LANG_NOT_SUPPORTED -> {
				close();
				installTtsData(ctx);
				throw new TextToSpeechException("Unsupported TTS language " + lang,
						TTS_ERR_LANG_NOT_SUPPORTED);
			}
		}
	}

	public <T> FutureSupplier<T> speak(CharSequence text) {
		return speak(text, null);
	}

	public <T> FutureSupplier<T> speak(CharSequence text, @Nullable T data) {
		return speak(text, data, null);
	}


	public <T> FutureSupplier<T> speak(CharSequence text, @Nullable T data,
																		 @Nullable Bundle params) {
		if (tts == null) return cancelled();
		if (promise != null) promise.cancel();
		Promise<T> p = new Promise<>();
		promise = p;
		uData = data;
		uId = String.valueOf(uCounter++);
		if (tts.speak(text, QUEUE_FLUSH, params, uId) != SUCCESS) {
			p.completeExceptionally(
					new TextToSpeechException("Failed to speak " + text, TTS_ERR_SPEAK_FAILED));
		}
		return p;
	}

	public void stop() {
		if (promise != null) promise.cancel();
		if (tts != null) tts.stop();
	}

	@Override
	public void close() {
		if (tts == null) return;
		if (promise != null) promise.cancel();
		tts.stop();
		tts.shutdown();
		tts = null;
		promise = null;
		uData = null;
		uId = null;
	}

	@SuppressWarnings("unchecked")
	private final class Listener extends UtteranceProgressListener
			implements android.speech.tts.TextToSpeech.OnInitListener {

		@Override
		public void onInit(int status) {
			Promise p = promise;
			if (p == null) return;
			promise = null;
			if (status == SUCCESS) {
				p.complete(TextToSpeech.this);
			} else {
				p.completeExceptionally(
						new TextToSpeechException("TTS initialization failed", TTS_ERR_INIT));
			}
		}

		@Override
		public void onStart(String utteranceId) {
		}

		@Override
		public void onDone(String utteranceId) {
			App.get().run(() -> {
				if ((promise == null) || !utteranceId.equals(uId)) return;
				Promise p = promise;
				Object d = uData;
				promise = null;
				uData = null;
				uId = null;
				p.complete(d);
			});
		}

		@Override
		public void onError(String utteranceId) {
			App.get().run(() -> {
				if ((promise == null) || !utteranceId.equals(uId)) return;
				Promise p = promise;
				promise = null;
				uData = null;
				uId = null;
				p.completeExceptionally(
						new TextToSpeechException("TTS speak failed", TTS_ERR_SPEAK_FAILED));
			});
		}

		@Override
		public void onError(String utteranceId, int errorCode) {
			App.get().run(() -> {
				if ((promise == null) || !utteranceId.equals(uId)) return;
				Promise p = promise;
				promise = null;
				uData = null;
				uId = null;
				p.completeExceptionally(
						new TextToSpeechException("TTS speak error " + errorCode, TTS_ERR_SPEAK_FAILED));
			});
		}

		@Override
		public void onStop(String utteranceId, boolean interrupted) {
			App.get().run(() -> {
				if ((promise == null) || !utteranceId.equals(uId)) return;
				Promise p = promise;
				promise = null;
				uData = null;
				uId = null;
				p.cancel();
			});
		}
	}
}
