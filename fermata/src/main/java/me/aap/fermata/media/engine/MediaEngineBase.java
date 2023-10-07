package me.aap.fermata.media.engine;

import static android.media.session.PlaybackState.STATE_PAUSED;
import static android.media.session.PlaybackState.STATE_PLAYING;
import static android.media.session.PlaybackState.STATE_STOPPED;
import static java.util.Collections.singletonList;
import static me.aap.fermata.media.sub.SubGrid.Position.BOTTOM_LEFT;
import static me.aap.fermata.media.sub.SubGrid.Position.BOTTOM_RIGHT;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.collection.CollectionUtils.comparing;
import static me.aap.utils.text.TextUtils.timeToString;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.sub.FileSubtitles;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.SubScheduler;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public abstract class MediaEngineBase implements MediaEngine {
	protected final Listener listener;
	protected VideoView videoView;
	private int state = STATE_STOPPED;
	private SubMgr subMgr;

	protected MediaEngineBase(Listener listener) {this.listener = listener;}

	@CallSuper
	@Override
	public void setVideoView(VideoView view) {
		if ((subMgr != null) && (videoView != null)) subMgr.removeSubtitleConsumer(videoView);
		videoView = view;
		if (view == null) return;
		if (subMgr != null) subMgr.addSubtitleConsumer(view);
		else selectSubtitleStream();
	}

	protected boolean isPlaying() {
		return state == STATE_PLAYING;
	}

	protected boolean isPaused() {
		return state == STATE_PAUSED;
	}

	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		var src = getSource();
		if (src == null) return completedEmptyList();

		var srcFile = src.getResource();
		var srcName = srcFile.getName();
		var idx = srcName.lastIndexOf('.');
		var baseName = (idx == -1) ? srcName : srcName.substring(0, idx);

		return srcFile.getParent().then(srcDir -> {
			var filter = srcDir.filterChildren();
			for (var ext : FileSubtitles.getSupportedFileExtensions())
				filter = filter.or().startsEnds(baseName, ext);
			return filter.apply();
		}).map(children -> {
			if (children.isEmpty()) return Collections.emptyList();

			int id = 0xFFFF;
			var list = new ArrayList<SubtitleStreamInfo>();
			Collections.sort(children, comparing(VirtualResource::getName));

			for (var f : children) {
				if (!f.isFile()) continue;
				var name = f.getName();
				var langStart = baseName.length() + 1;
				var langEnd = name.length() - 4;
				var lang = langStart >= langEnd ? null : name.substring(langStart, langEnd);
				list.add(new SubtitleStreamInfo(id++, lang, null, singletonList((VirtualFile) f)));
			}

			for (int i = 0, n = list.size(); i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (i == j) continue;
					var si1 = list.get(i);
					var si2 = list.get(j);
					list.add(new SubtitleStreamInfo(id++, si1 + " + " + si2, null,
							Arrays.asList(si1.getFiles().get(0), si2.getFiles().get(0))));
				}
			}

			return list;
		});
	}

	@Override
	public int getSubtitleDelay() {
		return (subMgr == null) ? 0 : subMgr.getSubtitleDelay();
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		if ((milliseconds == 0) && (subMgr == null)) return;
		sub().setSubtitleDelay(milliseconds);
	}

	@Override
	public boolean isSubtitlesSupported() {
		return true;
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		return (subMgr == null) ? null : subMgr.getCurrentSubtitleStreamInfo();
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
		if ((i == null) && (subMgr == null)) return;
		sub().setCurrentSubtitleStream(i);
	}

	@Override
	public FutureSupplier<SubGrid> getCurrentSubtitles() {
		return (subMgr == null) ? NO_SUBTITLES : subMgr.getCurrentSubtitles();
	}

	@Override
	public void addSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
		sub().addSubtitleConsumer(consumer);
	}

	@Override
	public void removeSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
		if (subMgr != null) subMgr.removeSubtitleConsumer(consumer);
	}

	@CallSuper
	@Override
	public void close() {
		stopped(false);
	}

	protected void started() {
		if (state == STATE_PLAYING) return;
		if (state == STATE_PAUSED) {
			if (subMgr != null) subMgr.start();
			state = STATE_PLAYING;
			return;
		}

		state = STATE_PLAYING;

		if (videoView != null) {
			if (subMgr != null) subMgr.addSubtitleConsumer(videoView);
			else selectSubtitleStream();
		}
	}

	protected void stopped(boolean paused) {
		if (state == STATE_STOPPED) return;

		if (paused) {
			if (state == STATE_PAUSED) return;
			state = STATE_PAUSED;
			if (subMgr != null) subMgr.stop(true);
		} else {
			state = STATE_STOPPED;
			videoView = null;
			if (subMgr != null) {
				subMgr.stop(false);
				subMgr = null;
			}
		}
	}

	protected void syncSub(long position, float speed, boolean restart) {
		if (subMgr != null) subMgr.sync(position, speed, restart);
	}

	private SubMgr sub() {
		if (subMgr == null) subMgr = new SubMgr();
		return subMgr;
	}

	private final class SubMgr implements BiConsumer<SubGrid.Position, Subtitles.Text> {
		private final List<BiConsumer<SubGrid.Position, Subtitles.Text>> consumers =
				new ArrayList<>(2);
		private int delay;
		private SubScheduler sub;
		private SubtitleStreamInfo streamInfo;
		private FutureSupplier<SubScheduler> loading = cancelled();

		int getSubtitleDelay() {
			return delay;
		}

		void setSubtitleDelay(int milliseconds) {
			if (delay == milliseconds) return;
			delay = milliseconds;
			if (sub != null) {
				getPosition().then(pos -> getSpeed().main().onSuccess(speed -> {
					if (sub != null) {
						sub.stop(false);
						sub.start(getSubtitleDelay(), getSubtitleDelay(), speed);
					}
				}));
			}
		}

		SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
			return streamInfo;
		}

		void setCurrentSubtitleStream(SubtitleStreamInfo i) {
			stop(false);
			streamInfo = i;

			if (videoView == null) {
				listener.onSubtitleStreamChanged(MediaEngineBase.this, i);
			} else {
				addSubtitleConsumer(videoView);
				load();
			}
		}

		FutureSupplier<SubGrid> getCurrentSubtitles() {
			return load().map(sub -> sub == null ? SubGrid.EMPTY : sub.getSubtitles());
		}

		void addSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
			if (consumers.contains(consumer)) return;
			consumers.add(consumer);
			if (sub == null) load();
			else if ((state == STATE_PLAYING) && !sub.isStarted()) start();
			else if (consumer == videoView) prepareDrawer();
		}

		void removeSubtitleConsumer(BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {
			if (!consumers.remove(consumer)) return;
			if (consumers.isEmpty()) stop(true);
			if (consumer == videoView) videoView.releaseSubDrawer();
		}

		private FutureSupplier<SubScheduler> load() {
			if (!loading.isCancelled()) return loading;
			var inf = streamInfo;
			if ((inf == null) || inf.getFiles().isEmpty()) return loading;

			return loading = App.get().execute(() -> {
				var src = getSource();
				if (src == null) return null;

				var files = inf.getFiles();
				var sg = FileSubtitles.load(files.get(0));

				if (files.size() == 1) {
					if (!src.isVideo()) sg.mergeAtPosition(BOTTOM_LEFT);
					return sg;
				}

				var sg1 = FileSubtitles.load(files.get(1));
				sg.mergeAtPosition(BOTTOM_LEFT);
				sg1.mergeAtPosition(BOTTOM_RIGHT);
				sg.mergeWith(sg1);

				if (src.isVideo()) {
					var s1 = sg.get(BOTTOM_LEFT);
					var s2 = sg.get(BOTTOM_RIGHT);
					if (s1.compareTime(s2)) {
						for (int i = 0, n = s1.size(); i < n; i++) {
							s1.get(i).setTranslation(s2.get(i).getText());
						}
						sg.remove(BOTTOM_RIGHT);
					}
				}

				return sg;
			}).main().map(sg -> {
				if ((sg == null) || (sub != null) || (inf != streamInfo)) return null;
				sub = new SubScheduler(App.get().getHandler(), sg, this);
				if ((state == STATE_PLAYING) && !consumers.isEmpty()) start();
				return sub;
			});
		}

		@Override
		public void accept(SubGrid.Position position, Subtitles.Text text) {
			for (var c : consumers) c.accept(position, text);

			if (BuildConfig.D) {
				getPosition().onSuccess(t -> {
					String time = timeToString((int) (t / 1000));

					if (text == null) {
						Log.d('[', time, "][", position, "] null");
					} else {
						Log.d('[', time, "][", position, "][", timeToString((int) (text.getTime() / 1000)),
								'-',
								timeToString((int) ((text.getTime() + text.getDuration()) / 1000)), "] ",
								text.getText());
					}
				});
			}
		}

		void start() {
			SubScheduler sub = this.sub;
			if (sub == null) return;
			getPosition().then(pos -> getSpeed().main().onSuccess(speed -> {
				if (sub != this.sub) return;
				for (var c : consumers) {
					if (c == videoView) {
						prepareDrawer();
						break;
					}
				}
				sub.start(pos, delay, speed);
			}));
		}

		void stop(boolean pause) {
			if (pause) {
				if (sub != null) sub.stop(true);
			} else {
				loading.cancel();
				loading = cancelled();
				if (sub != null) {
					sub.stop(false);
					sub = null;
				}
				consumers.clear();
			}
		}

		void sync(long position, float speed, boolean restart) {
			if (sub == null) return;
			if (restart) {
				sub.stop(false);
				sub.start(position, getSubtitleDelay(), speed);
			} else {
				sub.sync(position, getSubtitleDelay(), speed);
			}
		}

		private void prepareDrawer() {
			videoView.prepareSubDrawer((streamInfo != null) && (streamInfo.getFiles().size() == 2));
		}
	}
}
