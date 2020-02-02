package me.aap.fermata.engine.vlc;

import android.graphics.Rect;
import android.media.AudioManager;
import android.util.Log;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IMedia.VideoTrack;
import org.videolan.libvlc.interfaces.IVLCVout;

import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineException;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class VlcEngine implements MediaEngine, MediaPlayer.EventListener, SurfaceHolder.Callback,
		IVLCVout.OnNewVideoLayoutListener {
	@SuppressWarnings({"FieldCanBeLocal", "unused"}) // Hold reference to prevent garbage collection
	private final VlcEngineProvider provider;
	private final LibVLC vlc;
	private final MediaPlayer player;
	private final AudioEffects effects;
	private final Listener listener;
	private PlayableItem source;
	private VideoView videoView;
	private Media media;
	private long duration;
	private int videoWidth;
	private int videoHeight;
	private boolean playing;
	private long pendingPosition = -1;

	public VlcEngine(VlcEngineProvider provider, Listener listener) {
		LibVLC vlc = provider.getVlc();
		int sessionId = provider.getAudioSessionId();
		effects = (sessionId != AudioManager.ERROR) ? AudioEffects.create(0, sessionId) : null;
		this.provider = provider;
		this.vlc = vlc;
		this.listener = listener;
		player = new MediaPlayer(vlc);
		player.setEventListener(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_VLC;
	}

	@Override
	public void prepare(PlayableItem source) {
		this.source = source;

		try {
			Media m = media = new Media(vlc, source.getLocation());
			m.addOption(":input-fast-seek");

			if (m.isParsed()) {
				prepared(source, m);
			} else {
				if (source.isVideo()) m.addOption(":video-paused");
				m.setEventListener(e -> {
					if (m.isParsed()) prepared(source, m);
				});
				m.parseAsync();
			}
		} catch (Throwable ex) {
			if (media != null) {
				media.release();
				media = null;
			}

			listener.onEngineError(this, ex);
		}
	}

	private void prepared(PlayableItem source, Media media) {
		if (media != this.media) {
			media.release();
			return;
		}

		if (source.isVideo()) {
			for (int i = 0, n = media.getTrackCount(); i < n; i++) {
				IMedia.Track t = media.getTrack(i);
				if (t instanceof VideoTrack) {
					VideoTrack v = (VideoTrack) t;
					videoWidth = v.width;
					videoHeight = v.height;
				}
			}
		}

		playing = false;
		pendingPosition = -1;
		player.setMedia(media);
		duration = media.getDuration();
		media.release();
		this.media = null;
		listener.onEnginePrepared(this);
	}

	@Override
	public void start() {
		player.play();
	}

	@Override
	public void stop() {
		playing = false;
		pendingPosition = -1;
		player.stop();
		player.detachViews();
		source = null;

		if (media != null) {
			media.release();
			media = null;
		}
	}

	@Override
	public void pause() {
		player.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public long getDuration() {
		return duration;
	}

	@Override
	public long getPosition() {
		if (source != null) {
			return (pendingPosition == -1) ? (player.getTime() - source.getOffset()) : pendingPosition;
		} else {
			return 0;
		}
	}

	@Override
	public void setPosition(long position) {
		if (source != null) {
			if (playing) player.setTime(source.getOffset() + position);
			else pendingPosition = position;
		}
	}

	@Override
	public float getSpeed() {
		return player.getRate();
	}

	@Override
	public void setSpeed(float speed) {
		player.setRate(speed);
	}

	@Override
	public void setVideoView(VideoView view) {
		if (this.videoView != null) {
			this.videoView.getVideoSurface().getHolder().removeCallback(this);
		}

		this.videoView = view;
		IVLCVout out = player.getVLCVout();
		out.detachViews();

		if (view != null) {
			SurfaceHolder surface = view.getVideoSurface().getHolder();
			Rect size = surface.getSurfaceFrame();
			surface.addCallback(this);
			out.setVideoView(view.getVideoSurface());
			out.setSubtitlesView(view.getSubtitleSurface(true));
			out.attachViews(this);
			out.setWindowSize(size.width(), size.height());
			player.setAspectRatio(size.width() + ":" + size.height());
		}
	}

	@Override
	public float getVideoWidth() {
		return videoWidth;
	}

	@Override
	public float getVideoHeight() {
		return videoHeight;
	}

	@Override
	public AudioEffects getAudioEffects() {
		return effects;
	}

	@Override
	public void close() {
		stop();

		if (videoView != null) {
			videoView.getVideoSurface().getHolder().removeCallback(this);
			videoView = null;
		}

		player.release();
		if (effects != null) effects.release();
	}

	@Override
	public void onEvent(MediaPlayer.Event event) {
		switch (event.type) {
			case MediaPlayer.Event.Playing:
				playing = true;
				if (pendingPosition != -1) {
					setPosition(pendingPosition);
					pendingPosition = -1;
				}
				break;
			case MediaPlayer.Event.EndReached:
				listener.onEngineEnded(this);
				break;
			case MediaPlayer.Event.EncounteredError:
				listener.onEngineError(this, new MediaEngineException(""));
				break;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		try {
			player.getVLCVout().setWindowSize(width, height);
			player.setAspectRatio(width + ":" + height);
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to set window size and ratio", ex);
		}
	}

	@Override
	public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth,
															 int visibleHeight, int sarNum, int sarDen) {
		listener.onVideoSizeChanged(this, width, height);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if ((videoView != null) && (videoView.getVideoSurface().getHolder() == holder)) {
			holder.removeCallback(this);
			videoView = null;
			player.getVLCVout().detachViews();
		}
	}
}
