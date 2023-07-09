package me.aap.fermata.addon.felex.tutor;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.media.AudioManager;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import me.aap.fermata.addon.felex.R;
import me.aap.fermata.addon.felex.dict.Dict;
import me.aap.fermata.addon.felex.dict.Translation;
import me.aap.fermata.addon.felex.dict.Word;
import me.aap.fermata.addon.felex.view.FelexListView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.ToIntFunction;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.voice.SpeechToText;
import me.aap.utils.voice.SpeechToTextException;
import me.aap.utils.voice.TextToSpeech;

/**
 * @author Andrey Pavlenko
 */
public class DictTutor implements Closeable, AudioManager.OnAudioFocusChangeListener,
		View.OnClickListener, View.OnLongClickListener {
	public static final byte MODE_LISTENING = 0;
	public static final byte MODE_DIRECT = 1;
	public static final byte MODE_REVERSE = 2;
	public static final byte MODE_MIXED = 3;
	private static final byte STATE_RUNNING = 1;
	private static final byte STATE_PAUSING_BY_FOCUS = 2;
	private static final byte STATE_PAUSED_BY_FOCUS = 3;
	private static final byte STATE_PAUSING_BY_USER = 4;
	private static final byte STATE_PAUSED_BY_USER = 5;
	private static final byte STATE_CLOSED = 6;
	private final MainActivityDelegate activity;
	private final Dict dict;
	private final byte mode;
	private final TextToSpeech dirTts;
	private final TextToSpeech revTts;
	@Nullable
	private final SpeechToText dirStt;
	@Nullable
	private final SpeechToText revStt;
	private final Random rnd;
	private final AudioFocusRequestCompat audioFocusReq;
	private final FelexListView listView;
	private final ToIntFunction<Word> getProgress;
	private final Deque<Word> history = new LinkedList<>();
	private TextView transView;
	private Task current;
	private TextView wordView;
	private byte state;

	private DictTutor(MainActivityDelegate activity, Dict dict, byte mode,
										TextToSpeech dirTts, TextToSpeech revTts) {
		this.activity = activity;
		this.dict = dict;
		this.mode = mode;
		this.dirTts = dirTts;
		this.revTts = revTts;
		this.dirStt = ((mode == MODE_DIRECT) || (mode == MODE_MIXED))
				? new SpeechToText(activity.getContext(), dict.getTargetLang()) : null;
		this.revStt = ((mode == MODE_REVERSE) || (mode == MODE_MIXED))
				? new SpeechToText(activity.getContext(), dict.getSourceLang()) : null;
		rnd = new Random();

		AudioAttributesCompat attrs = new AudioAttributesCompat.Builder()
				.setUsage(AudioAttributesCompat.USAGE_MEDIA)
				.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
				.build();
		audioFocusReq = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
				.setAudioAttributes(attrs)
				.setWillPauseWhenDucked(false)
				.setOnAudioFocusChangeListener(this)
				.build();
		listView = activity.findViewById(R.id.felix_list_view);
		getProgress = (mode == MODE_DIRECT) ? Word::getDirProgress
				: (mode == MODE_REVERSE) ? Word::getRevProgress : Word::getProgress;
	}

	public static FutureSupplier<DictTutor> create(MainActivityDelegate activity, Dict dict,
																								 byte mode) {
		Context ctx = activity.getContext();
		return MainActivityDelegate.getActivityDelegate(ctx).then(a ->
				activity.isCarActivity() ? completed(true) : a.getAppActivity()
						.checkPermissions(RECORD_AUDIO).map(r -> {
							if (r[0] == PERMISSION_GRANTED) return true;
							showAlert(ctx, me.aap.fermata.R.string.err_no_audio_record_perm);
							return false;
						})
		).then(p -> {
			if (!p) throw new IllegalStateException("Failed to request RECORD_AUDIO permission");

			return TextToSpeech.create(ctx, dict.getSourceLang())
					.then(dir ->
							TextToSpeech.create(ctx, dict.getTargetLang()).onFailure(err -> dir.close())
									.map(rev -> new DictTutor(activity, dict, mode, dir, rev)))
					.onFailure(err -> showAlert(ctx, err.getLocalizedMessage()));
		});
	}

	public void start() {
		MediaSessionCallback cb = activity.getMediaSessionCallback();
		if (cb.isPlaying()) cb.onPause();
		activity.getContextMenu().show(b -> {
			ViewGroup v = (ViewGroup) b.inflate(R.layout.dict_tutor);
			wordView = v.findViewById(R.id.word);
			transView = v.findViewById(R.id.trans);
			b.setCloseHandlerHandler(m -> close());
			v.setOnClickListener(this);
			v.setOnLongClickListener(this);
			setState(STATE_RUNNING);
			requestAudioFocus();
			activity.keepScreenOn(true);
			speak();
		});
	}

	@Override
	public void close() {
		current = null;
		releaseAudioFocus();
		activity.keepScreenOn(false);
		setState(STATE_CLOSED);
		IoUtils.close(dirTts, revTts, dirStt, revStt);
	}

	public boolean isClosed() {
		return state == STATE_CLOSED;
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			switch (getState()) {
				case STATE_PAUSING_BY_FOCUS:
					setState(STATE_RUNNING);
					return;
				case STATE_PAUSED_BY_FOCUS:
					setState(STATE_RUNNING);
					speak(current);
			}
		} else if (getState() == STATE_RUNNING) {
			setState(STATE_PAUSING_BY_FOCUS);
		}
	}

	@Override
	public void onClick(View v) {
		if (current != null) current.attempt++;
	}

	@Override
	public boolean onLongClick(View v) {
		if (current == null) return false;
		switch (getState()) {
			case STATE_RUNNING:
			case STATE_PAUSING_BY_FOCUS:
				setState(STATE_PAUSING_BY_USER);
				return true;
			case STATE_PAUSING_BY_USER:
				setState(STATE_RUNNING);
				return true;
			case STATE_PAUSED_BY_FOCUS:
				return true;
			case STATE_PAUSED_BY_USER:
				setState(STATE_RUNNING);
				speak(current);
			default:
				return false;
		}
	}

	private void requestAudioFocus() {
		AudioManager am = (AudioManager) activity.getContext().getSystemService(Context.AUDIO_SERVICE);
		if (am == null) return;
		AudioManagerCompat.requestAudioFocus(am, audioFocusReq);
	}

	private void releaseAudioFocus() {
		AudioManager am = (AudioManager) activity.getContext().getSystemService(Context.AUDIO_SERVICE);
		if (am == null) return;
		AudioManagerCompat.requestAudioFocus(am, audioFocusReq);
	}

	private void speak() {
		if (isClosed()) return;
		nextTask().onSuccess(this::speak);
	}

	private void speak(Task t) {
		if (isClosed()) return;
		current = t;
		setWordText(t.speak);
		(t.direct ? dirTts : revTts).speak(t.speak, t).onCompletion(this::speakHandler);
	}

	private FutureSupplier<Void> speakTrans(Task t) {
		if (isClosed()) return completedVoid();
		return Async.forEach(tr -> {
			String text = tr.getTranslation();
			setTransText(text);
			return revTts.speak(text);
		}, t.trans);
	}

	private void speakHandler(Task t, Throwable err) {
		if (isClosed()) return;
		if (err != null) {
			Log.e(err, "Speech failed");
			showAlert(getContext(), err.getLocalizedMessage());
			close();
		} else if (checkState()) {
			if (mode == MODE_LISTENING) {
				speakTrans(t).onSuccess(v -> activity.postDelayed(this::speak, 1000));
			} else {
				assert (t.direct ? dirStt : revStt) != null;
				(t.direct ? dirStt : revStt).recognize(t)
						.onProgress((r, p, c) -> setTransText(r.getText()))
						.onCompletion(this::recognizeHandler);
			}
		}
	}

	private void recognizeHandler(SpeechToText.Result<Task> r, Throwable err) {
		if (isClosed()) return;
		if (err != null) {
			Log.e(err);
			if (err instanceof SpeechToTextException) {
				SpeechToTextException e = (SpeechToTextException) err;
				if (e.getErrorCode() == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
					showAlert(getContext(), me.aap.fermata.R.string.err_no_audio_record_perm);
				} else {
					if (!checkState()) return;
					setTransText(e.getLocalizedMessage());
					Task t = current;
					if (t != null) activity.postDelayed(() -> speak(t), 15000);
				}
			}
		} else {
			if (!checkState()) return;
			Task t = r.getData();
			String text = r.getText();
			setTransText(text);

			if (t.validTranslation(text)) {
				t.w.incrProgress(dict, t.direct, 10);
				listView.onProgressChanged(dict, t.w);
				activity.postDelayed(this::speak, 1000);
			} else {
				activity.postDelayed(() -> retry(t), 2000);
			}
		}
	}

	private void retry(Task t) {
		if (isClosed()) return;
		if (++t.attempt < 2) {
			setTransText(null);
			speak(t);
		} else {
			FutureSupplier<?> f;
			if (t.direct) {
				f = speakTrans(t);
			} else {
				String text = t.w.getExpr();
				setTransText(text);
				f = dirTts.speak(text);
			}

			t.w.incrProgress(dict, t.direct, -10);
			listView.onProgressChanged(dict, t.w);
			f.thenRun(() -> activity.postDelayed(this::speak, 1000));
		}
	}

	private void setWordText(CharSequence text) {
		wordView.setText(text);
		transView.setVisibility(GONE);
	}

	private void setTransText(CharSequence text) {
		transView.setText(text);
		transView.setVisibility((text == null) ? GONE : VISIBLE);
	}

	private Context getContext() {
		return activity.getContext();
	}

	private FutureSupplier<Task> nextTask() {
		return dict.getRandomWord(rnd, history, getProgress).then(w -> w.getTranslations(dict)
				.map(tr -> new Task(w, tr))).main().onFailure(err -> {
			if (isClosed()) return;
			if (err != null) {
				Log.e(err);
				showAlert(getContext(), "Failed to load a word: " + err);
				close();
			}
		});
	}

	private byte getState() {
		return state;
	}

	private void setState(byte state) {
		this.state = state;
	}

	private boolean checkState() {
		switch (getState()) {
			case STATE_RUNNING:
				return true;
			case STATE_PAUSING_BY_FOCUS:
				setState(STATE_PAUSED_BY_FOCUS);
				return false;
			case STATE_PAUSING_BY_USER:
				setState(STATE_PAUSED_BY_USER);
				releaseAudioFocus();
				return false;
			default:
				return false;
		}
	}

	private final class Task {
		final Word w;
		final List<Translation> trans;
		final String speak;
		final boolean direct;
		byte attempt;

		Task(Word w, List<Translation> trans) {
			this.w = w;
			this.trans = trans;

			switch (mode) {
				case MODE_LISTENING:
				case MODE_DIRECT:
					speak = w.getExpr();
					direct = true;
					break;
				case MODE_MIXED:
					int dir = w.getDirProgress();
					int rev = w.getRevProgress();
					if ((dir == rev) ? rnd.nextBoolean() : (dir < rev)) {
						speak = w.getExpr();
						direct = true;
						break;
					}
				case MODE_REVERSE:
					speak = (trans.isEmpty()) ? w.getExpr()
							: trans.get(rnd.nextInt(trans.size())).getTranslation();
					direct = false;
					break;
				default:
					throw new IllegalStateException();
			}
		}

		boolean validTranslation(String text) {
			if (text == null) return false;
			if (direct) {
				for (Translation t : trans) {
					if (t.matches(text)) return true;
				}
				return false;
			} else {
				return w.matches(text);
			}
		}
	}
}