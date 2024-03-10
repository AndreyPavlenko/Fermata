package me.aap.fermata.addon.felex.media;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.function.Cancellable.CANCELED;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.felex.tutor.DictTutor;
import me.aap.fermata.media.engine.MediaEngineBase;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;

public class FelexMediaEngine extends MediaEngineBase implements BiConsumer<String, String> {
	private final MediaSessionCompat session;
	private Cancellable loading = CANCELED;
	@Nullable
	private FelexItem.Tutor source;
	@Nullable
	private DictTutor tutor;

	protected FelexMediaEngine(Listener listener) {
		super(listener);
		session = (listener instanceof MediaSessionCallback cb) ? cb.getSession() : null;
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_FELEX;
	}

	@Override
	public void prepare(PlayableItem source) {
		if (source instanceof FelexItem.Tutor ti) {
			if (tutor != null) {
				if (ti.isPrev()) {
					tutor.prev(false);
					listener.onEnginePrepared(this);
					return;
				} else if (ti.isNext()) {
					tutor.next(false);
					listener.onEnginePrepared(this);
					return;
				}
			}

			stop();
			this.source = ti;
			DictTutor.create(ti.getParent().getDict(), ti.getMode()).onCompletion((tutor, err) -> {
				if (err != null) {
					if (isCancellation(err)) return;
					listener.onEngineError(this, err);
				} else {
					if (this.tutor != null) this.tutor.close();
					this.tutor = tutor;
					tutor.setTextConsumer(this);
					listener.onEnginePrepared(this);
				}
			});
		} else {
			stop();
			listener.onEngineError(this, new IllegalArgumentException("Unsupported source: " + source));
		}
	}

	@Override
	public void setVideoView(VideoView view) {
		super.setVideoView(view);
		if (view == null) return;
		view.clearVideoSurface();
		view.prepareSubDrawer(true);
		if (tutor != null) tutor.setTextConsumer(this);
	}

	@Override
	public void start() {
		if (tutor != null) {
			started();
			tutor.start();
			listener.onEngineStarted(this);
		} else {
			listener.onEngineError(this, new IllegalStateException("Engine is not prepared"));
		}
	}

	@Override
	public void stop() {
		stopped(false);
		loading.cancel();
		loading = CANCELED;
		if (tutor == null) return;
		tutor.close();
		source = null;
		tutor = null;
	}

	@Override
	public void close() {
		stop();
		super.close();
	}

	@Override
	public void pause() {
		if (tutor != null) {
			tutor.pause();
			stopped(true);
		} else {
			stopped(false);
		}
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed(0L);
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return completed(0L);
	}

	@Override
	public void setPosition(long position) {
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(1F);
	}

	@Override
	public void setSpeed(float speed) {
	}

	@Override
	public float getVideoWidth() {
		if (videoView == null) return 0;
		return (videoView.getParent() instanceof ViewGroup p) ? p.getWidth() : videoView.getWidth();
	}

	@Override
	public float getVideoHeight() {
		if (videoView == null) return 0;
		return (videoView.getParent() instanceof ViewGroup p) ? p.getHeight() : videoView.getHeight();
	}

	@Override
	public void accept(@NonNull String text, @Nullable String trans) {
		if (videoView != null) {
			var t = new Subtitles.Text(text, 0, 0);
			t.setTranslation(trans);
			videoView.accept(SubGrid.Position.MIDDLE_CENTER, t);
		}
		if ((source != null) && (session != null)) {
			var dict = source.getParent();
			var icon = dict.iconBitmap();
			var b = new MediaMetadataCompat.Builder();
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, text);
			if (trans != null) b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, trans);
			if (icon != null) b.putBitmap(METADATA_KEY_ALBUM_ART, icon);
			session.setMetadata(b.build());
		}
	}

	@Override
	public boolean muteOnTransientFocusLoss() {
		return true;
	}

	@Override
	public void mute(Context ctx) {
	}

	@Override
	public void unmute(Context ctx) {
	}
}
