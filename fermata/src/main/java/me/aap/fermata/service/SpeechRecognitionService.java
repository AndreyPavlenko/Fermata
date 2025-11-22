package me.aap.fermata.service;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.speech.SpeechRecognizer.ERROR_AUDIO;
import static android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
import static android.speech.SpeechRecognizer.RESULTS_RECOGNITION;
import static java.nio.ByteOrder.nativeOrder;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.SubGenAddon;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.SharedTextBuilder;

public class SpeechRecognitionService extends RecognitionService {
	private static final int CHUNK_LEN = 5;
	private PreferenceStore prefs = new BasicPreferenceStore();
	private Thread readerThread;
	private ByteBuffer buffer;

	{
		prefs.applyIntPref(SubGenAddon.CHUNK_LEN, CHUNK_LEN);
	}

	@Override
	@SuppressLint("MissingPermission")
	protected synchronized void onStartListening(Intent intent, Callback listener) {
		onStopListening(listener);
		var lang = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
		prefs.applyStringPref(SubGenAddon.LANG, lang != null ? lang.split("-")[0] : "auto");
		var a = MainActivity.getActiveInstance();
		var permCheck = a == null || a.isCarActivity() ? completed(new int[]{PERMISSION_GRANTED}) :
				a.checkPermissions(RECORD_AUDIO);
		permCheck.then(r -> {
			if (r[0] != PERMISSION_GRANTED) {
				listener.error(ERROR_INSUFFICIENT_PERMISSIONS);
				return completedNull();
			} else {
				return AddonManager.get().getOrInstallAddon(SubGenAddon.class)
						.then(sg -> sg.getTranscriptor(prefs));
			}
		}).then(tr -> {
			if (tr == null) return completedNull();
			AudioRecord audioRecord;
			try {
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
						16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
						CHUNK_LEN * 2 * 16000);
			} catch (IllegalArgumentException ignored) {
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
						44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
						CHUNK_LEN * 2 * 16000);
			}
			var ar = audioRecord;
			return App.get().execute(() -> reader(ar, tr, listener));
		}).onFailure(err -> {
			try {
				Log.e(err);
				listener.error(ERROR_AUDIO);
			} catch (RemoteException ex) {
				Log.e(ex);
			}
		});
	}

	private void reader(AudioRecord record, SubGenAddon.Transcriptor tr, Callback listener) {
		Log.d("Speech recognition started");
		var thread = Thread.currentThread();
		var bytesPerSample = 2;
		var rate = record.getSampleRate();
		var channels = record.getChannelCount();
		var bufLen = CHUNK_LEN * bytesPerSample * channels * rate;
		var maxRead = bufLen * 5;
		synchronized (this) {
			if (readerThread != null) return;
			readerThread = thread;
			if (buffer == null || buffer.capacity() < bufLen) {
				buffer = ByteBuffer.allocateDirect(bufLen).order(nativeOrder());
			}
		}

		tr.reset();
		String fullText = "";
		try {
			int read = 0;
			buffer.clear();
			listener.beginningOfSpeech();
			record.startRecording();
			for (;;) {
				int i = record.read(buffer, buffer.capacity());
				if (i <= 0) {
					Log.d("AudioRecord read error: ", i);
					break;
				}
				if (read >= maxRead) {
					Log.d("Max speech length reached");
					break;
				}

				synchronized (this) {
					if (readerThread != thread) {
						Log.d("Speech recognition interrupted");
						break;
					}
				}

				read += i;
				buffer.limit(i);

				if (!tr.read(buffer, CHUNK_LEN, 2, channels, rate)) {
					assert !buffer.hasRemaining() : "Buffer is not fully consumed: " + buffer;
					buffer.clear();
					continue;
				}

				var trans = tr.transcribe();
				if (buffer.hasRemaining()) {
					tr.read(buffer, CHUNK_LEN, 2, channels, rate);
				}

				assert !buffer.hasRemaining() : "Buffer is not fully consumed: " + buffer;
				buffer.clear();

				if (trans.isEmpty()) continue;

				String text;
				try (var tb = SharedTextBuilder.get()) {
					for (var t : trans) {
						tb.append(t.getText()).append(' ');
					}
					tb.setLength(tb.length() - 1);
					text = tb.toString();
				}
				Log.d(text);
				var result = new Bundle();
				var list = new ArrayList<String>(1);
				list.add(text);
				result.putStringArrayList(RESULTS_RECOGNITION, list);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					listener.segmentResults(result);
					fullText = fullText.isEmpty() ? text : fullText + ' ' + text;
				} else {
					listener.results(result);
					break;
				}
			}

			var result = new Bundle();
			var list = new ArrayList<String>(1);
			list.add(fullText);
			result.putStringArrayList(RESULTS_RECOGNITION, list);
			listener.results(result);
		} catch (Throwable err) {
			Log.e(err);
		} finally {
			try {
				listener.endOfSpeech();
			} catch (RemoteException err) {
				Log.e(err);
			}
			synchronized (this) {
				if (readerThread == thread) readerThread = null;
			}
			//noinspection ResultOfMethodCallIgnored
			Thread.interrupted();
			record.stop();
			record.release();
			tr.release();
		}
		Log.d("Speech recognition stopped");
	}

	@Override
	protected synchronized void onCancel(Callback listener) {
		onStopListening(listener);
	}

	@Override
	protected synchronized void onStopListening(Callback listener) {
		if (readerThread != null) {
			readerThread.interrupt();
			readerThread = null;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		prefs = new BasicPreferenceStore() {
			final PreferenceStore parent =
					SharedPreferenceStore.create(SpeechRecognitionService.this, "medialib");

			@Override
			public String getStringPref(Pref<? extends Supplier<String>> pref) {
				return (pref == SubGenAddon.LANG) ? super.getStringPref(pref) : parent.getStringPref(pref);
			}
		};
		prefs.applyIntPref(SubGenAddon.CHUNK_LEN, 1);
	}
}
