package me.aap.fermata.addon.felex.tutor;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.speech.RecognizerIntent.EXTRA_LANGUAGE;
import static android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE;
import static android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS;
import static android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS;
import static java.util.Collections.unmodifiableList;
import static me.aap.fermata.R.string.err_no_audio_record_perm;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.Cancellable.CANCELED;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.content.Context;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.felex.FelexAddon;
import me.aap.fermata.addon.felex.dict.Dict;
import me.aap.fermata.addon.felex.dict.DictInfo;
import me.aap.fermata.addon.felex.dict.Translation;
import me.aap.fermata.addon.felex.dict.Word;
import me.aap.fermata.addon.felex.dict.WordMatcher;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Function;
import me.aap.utils.function.Supplier;
import me.aap.utils.function.ToIntFunction;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.voice.SpeechToText;
import me.aap.utils.voice.SpeechToTextException;
import me.aap.utils.voice.TextToSpeech;

/**
 * @author Andrey Pavlenko
 */
public class DictTutor implements Closeable {
	private final Dict dict;
	private final DictInfo dictInfo;
	private final Mode mode;
	private final TextToSpeech dirTts;
	private final TextToSpeech revTts;
	private final SpeechToText stt;
	private final Random rnd;
	private final int queueSize;
	private final List<Word> queue;
	private final Supplier<FutureSupplier<Integer>> batchFunc;
	private int cursor;
	private Cancellable running = CANCELED;
	private Task curTask;
	private String curText;
	private String curTrans;
	private BiConsumer<String, String> textConsumer;
	private boolean offlineSupported = true;


	private DictTutor(Context ctx, Dict dict, Mode mode, TextToSpeech dirTts, TextToSpeech revTts) {
		this.dict = dict;
		this.dictInfo = dict.getInfo();
		this.mode = mode;
		this.dirTts = dirTts;
		this.revTts = revTts;
		stt = new SpeechToText(ctx, null, FelexAddon.get().getSttServiceName());
		rnd = new Random();
		queueSize = (mode == Mode.LISTENING) ? 100 : dictInfo.getBatchSize() * 2;
		queue = new ArrayList<>(queueSize);

		ToIntFunction<Word> getProgress =
				(mode == Mode.DIRECT || mode == Mode.LISTENING) ? Word::getDirProgress :
						(mode == Mode.REVERSE) ? Word::getRevProgress : Word::getProgress;
		var batchType = (mode == Mode.LISTENING) ? DictInfo.BATCH_TYPE_RND : dictInfo.getBatchType();

		if (batchType.equals(DictInfo.BATCH_TYPE_RND)) {
			batchFunc = () -> dict.getRandomWords(rnd, queue, dictInfo.getBatchSize(), getProgress);
		} else if (batchType.equals(DictInfo.BATCH_TYPE_LEAST)) {
			batchFunc = () -> dict.getLeastProgressWords(queue, dictInfo.getBatchSize(), getProgress);
		} else {
			ToIntFunction<Dict> getDictProgress =
					mode == Mode.DIRECT ? Dict::getDirProgress :
							(mode == Mode.REVERSE) ? Dict::getRevProgress : Dict::getProgress;
			batchFunc = () -> {
				if (getDictProgress.applyAsInt(dict) < 80) {
					return dict.getLeastProgressWords(queue, dictInfo.getBatchSize(), getProgress);
				} else {
					return dict.getRandomWords(rnd, queue, dictInfo.getBatchSize(), getProgress);
				}
			};
		}
	}

	public static FutureSupplier<DictTutor> create(Dict dict, Mode mode) {
		return checkRecordPerm().then(granted -> {
			if (!granted) return failed(new IllegalStateException(getString(err_no_audio_record_perm)));
			var ctx = FermataApplication.get();
			return TextToSpeech.create(ctx, dict.getSourceLang()).then(
					dir -> TextToSpeech.create(ctx, dict.getTargetLang()).onFailure(err -> dir.close())
							.map(rev -> new DictTutor(ctx, dict, mode, dir, rev)));
		});
	}

	public void setTextConsumer(@Nullable BiConsumer<String, String> textConsumer) {
		this.textConsumer = textConsumer;
		if ((textConsumer != null) && (curText != null)) textConsumer.accept(curText, curTrans);
	}

	public void start() {
		nextTask();
	}

	public void pause() {
		if (dirTts != null) dirTts.stop();
		if (revTts != null) revTts.stop();
		stt.stop();
		running.cancel();
	}

	public void prev(boolean start) {
		pause();
		curTask = null;
		cursor = Math.max(0, cursor - 2);
		if (start) start();
	}

	public void next(boolean start) {
		pause();
		curTask = null;
		if (start) start();
	}

	@Override
	public void close() {
		running.cancel();
		textConsumer = null;
		IoUtils.close(dirTts, revTts, stt);
	}

	private void nextTask() {
		if ((curTask != null) && !curTask.isDone()) {
			speak(curTask);
		} else {
			FutureSupplier<Task> f;
			int s = queue.size();
			if (cursor < s) {
				var w = queue.get(cursor++);
				f = w.getTranslations(dict).map(tr -> new Task(w, tr));
			} else {
				int batchSize = dictInfo.getBatchSize();
				int remain = queueSize - s;
				if (remain < batchSize) queue.subList(0, batchSize - remain).clear();
				cursor = queue.size();
				f = batchFunc.get().then(ignore -> {
					assert (queue.size() <= queueSize);
					if (cursor >= queue.size())
						return failed(new UnsupportedOperationException("Empty dictionary!"));
					var w = queue.get(cursor++);
					return w.getTranslations(dict).map(tr -> new Task(w, tr));
				});
			}
			run(f.main().onCompletion(this::nextTaskHandler));
		}
	}

	private void nextTaskHandler(@Nullable Task t, @Nullable Throwable err) {
		if (err != null) {
			if (isCancellation(err)) return;
			error("Failed to load a word: " + err);
			schedule(this::nextTask, 5000);
		} else {
			assert t != null;
			speak(curTask = t);
		}
	}

	private void speak(Task t) {
		setText(t.speak, null);
		run((t.direct ? dirTts : revTts).speak(t.speak, t).onCompletion(this::speakHandler));
	}

	private void speakHandler(@Nullable Task t, @Nullable Throwable err) {
		if (err != null) {
			if (isCancellation(err)) return;
			Log.e(err, "Speech failed");
			error(err.getLocalizedMessage());
			schedule(() -> {
				if (curTask == null) nextTask();
				else speak(curTask);
			}, 5000);
		} else if (mode == Mode.LISTENING) {
			assert t != null;
			curTask = null;
			speakTrans(t);
		} else {
			assert t != null;
			recognize(t);
		}
	}

	private void speakTrans(Task t) {
		var f = Async.forEach(tr -> {
			String text = tr.getTranslation();
			setText(t.speak, text);
			return revTts.speak(text);
		}, t.trans).onCompletion(this::speakTransHandler);
		run(f);
	}

	private void speakTransHandler(Object ignore, @Nullable Throwable err) {
		if (err != null) {
			if (isCancellation(err)) return;
			Log.e(err, "Translation speech failed");
			error(err.getLocalizedMessage());
			schedule(this::nextTask, 5000);
		} else {
			schedule(this::nextTask, 1000);
		}
	}

	private void recognize(Task t) {
		var lang = t.direct ? dict.getTargetLang() : dict.getSourceLang();
		var intent = stt.getRecognizerIntent();
		intent.putExtra(EXTRA_LANGUAGE, lang.toLanguageTag());
		intent.putExtra(EXTRA_PREFER_OFFLINE, offlineSupported && FelexAddon.get().isOfflineMode());
		intent.putExtra(EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
		intent.putExtra(EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000);
		var f = stt.recognize(t).onProgress(this::incompleteRecognitionHandler)
				.onCompletion(this::recognizeHandler);
		run(f);
	}

	private void incompleteRecognitionHandler(SpeechToText.Result<Task> incomplete, int ignore1,
																						int ignore2) {
		var t = incomplete.getData();
		var text = incomplete.getText();
		var matched = match(text, t::validTranslation);

		if ((matched != null) || ((matched = match(text, dictInfo::isAckPhrase)) != null)) {
			setText(t.speak, matched);
			stt.stop();
			curTask = null;
			t.w.incrProgress(dict, t.direct, 10);
			schedule(this::nextTask, 1000);
		} else if ((matched = match(text, dictInfo::isSkipPhrase)) != null) {
			setText(t.speak, matched);
			stt.stop();
			t.attempt = 2;
			retry(t);
		} else {
			setText(t.speak, text.get(0));
		}
	}

	private void recognizeHandler(@Nullable SpeechToText.Result<Task> r, @Nullable Throwable err) {
		if (err != null) {
			if (isCancellation(err)) return;

			if (offlineSupported && (err instanceof SpeechToTextException stte) &&
					(stte.getErrorCode() == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)) {
				Log.e("ERROR_LANGUAGE_NOT_SUPPORTED received, disabling offline mode");
				offlineSupported = false;
				if (curTask != null) {
					recognize(curTask);
					return;
				}
			}

			Log.e(err, "Speech recognition failed");
			if (err instanceof SpeechToTextException e) {
				if (e.getErrorCode() == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
					error(getString(err_no_audio_record_perm));
					run(checkRecordPerm().onSuccess(b -> schedule(() -> {
						if (curTask == null) {
							nextTask();
						} else {
							if (b) setText(curTask.speak, null);
							recognize(curTask);
						}
					}, 10000)));
				} else {
					var msg = e.getLocalizedMessage();
					setText((msg == null) ? e.toString() : msg, null);
					schedule(() -> {
						if (curTask == null) {
							nextTask();
						} else {
							setText(curTask.speak, null);
							recognize(curTask);
						}
					}, 5000);
				}
			}
		} else {
			assert r != null;
			var t = r.getData();
			var text = r.getText();
			var matched = match(text, t::validTranslation);

			if ((matched != null) || ((matched = match(text, dictInfo::isAckPhrase)) != null)) {
				setText(t.speak, matched);
				curTask = null;
				t.w.incrProgress(dict, t.direct, 10);
				schedule(this::nextTask, 1000);
			} else if ((matched = match(text, dictInfo::isSkipPhrase)) != null) {
				setText(t.speak, matched);
				t.attempt = 2;
				retry(t);
			} else {
				setText(t.speak, text.get(0));
				t.attempt++;
				schedule(() -> retry(t), 1000);
			}
		}
	}

	@Nullable
	private String match(List<String> text, Function<String, Boolean> f) {
		for (var t : text) {
			if (t == null) continue;
			if (f.apply(t)) return t;
		}
		return null;
	}

	private void retry(Task t) {
		if (t.isDone()) {
			t.w.incrProgress(dict, t.direct, -10);
			if (t.direct) {
				speakTrans(t);
			} else {
				String text = t.w.getExpr();
				setText(t.speak, text);
				run(dirTts.speak(text).onCompletion(this::speakTransHandler));
			}
		} else {
			setText(t.speak, null);
			speak(t);
		}
	}

	private void setText(@NonNull String text, @Nullable String trans) {
		curText = text;
		curTrans = trans;
		if (textConsumer != null) textConsumer.accept(text, trans);
	}

	private void error(String err) {
		Log.e(err);
		setText(err, null);
	}

	public void run(Cancellable c) {
		running.cancel();
		running = c;
	}

	private void schedule(Runnable r, long delay) {
		run(FermataApplication.get().getHandler().schedule(r, delay));
	}

	private static FutureSupplier<Boolean> checkRecordPerm() {
		var a = MainActivity.getActiveInstance();
		return (a == null) ? completed(true) :
				a.checkPermissions(RECORD_AUDIO).map(r -> (r[0] == PERMISSION_GRANTED));
	}

	private static String getString(@StringRes int resId) {
		return FermataApplication.get().getString(resId);
	}

	public enum Mode {
		DIRECT, REVERSE, LISTENING, MIXED;

		public static final List<Mode> values = unmodifiableList(Arrays.asList(values()));
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
				case LISTENING:
				case DIRECT:
					speak = w.getExpr();
					direct = true;
					break;
				case MIXED:
					if (rnd.nextBoolean()) {
						speak = w.getExpr();
						direct = true;
						break;
					}
				case REVERSE:
					speak = (trans.isEmpty()) ? w.getExpr() :
							trans.get(rnd.nextInt(trans.size())).getTranslation();
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
					if (WordMatcher.matches(dictInfo, t, text)) return true;
				}
				return false;
			} else {
				return WordMatcher.matches(dictInfo, w, text);
			}
		}

		boolean isDone() {
			return attempt > 1;
		}
	}
}