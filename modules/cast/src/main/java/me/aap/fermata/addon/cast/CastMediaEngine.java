package me.aap.fermata.addon.cast;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_TOGGLE_MUTE;
import static com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED;
import static com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE;
import static com.google.android.gms.cast.MediaMetadata.KEY_TITLE;
import static com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE;
import static com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
import static com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED;
import static com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE;
import static java.util.Collections.emptyList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.collection.CollectionUtils.boxed;
import static me.aap.utils.collection.CollectionUtils.contains;
import static me.aap.utils.collection.CollectionUtils.unboxed;

import android.annotation.SuppressLint;
import android.media.AudioManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import com.google.android.gms.cast.MediaError;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaSeekOptions;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineException;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("VisibleForTests")
public class CastMediaEngine extends RemoteMediaClient.Callback implements MediaEngine {
	private final CastServer server;
	private final CastSession session;
	private final RemoteMediaClient client;
	MediaEngine.Listener listener;
	private PlayableItem source;
	private float speed;
	private double volume;

	public CastMediaEngine(CastServer server, CastSession session, RemoteMediaClient client,
												 Listener listener) {
		this.server = server;
		this.session = session;
		this.client = client;
		this.listener = listener;
		client.registerCallback(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_CAST;
	}

	@Override
	public void prepare(PlayableItem source) {
		this.source = source;
		VirtualResource src;

		if (source instanceof MediaLib.ArchiveItem a) {
			long start = a.getStartTime();
			Uri uri = a.getParent().getLocation(start, a.getEndTime() - start);
			src = GenericFileSystem.getInstance().getResource(Rid.create(uri)).getOrThrow();
		} else {
			src = source.getResource();
		}

		source.getMediaDescription().then(dsc -> source.getDuration()
				.then(dur -> server.setContent(src, dsc.getIconUri()).onSuccess(url -> {
					int type = source.isVideo() ? MEDIA_TYPE_MOVIE : MEDIA_TYPE_MUSIC_TRACK;
					MediaMetadata meta = new MediaMetadata(type);
					meta.putString(KEY_TITLE, String.valueOf(dsc.getTitle()));
					meta.putString(KEY_SUBTITLE, String.valueOf(dsc.getSubtitle()));
					if (url[1] != null) meta.addImage(new WebImage(Uri.parse(url[1])));
					MediaInfo info =
							new MediaInfo.Builder(url[0]).setStreamType(STREAM_TYPE_BUFFERED).setMetadata(meta)
									.setStreamDuration(dur).build();
					client.load(new MediaLoadRequestData.Builder().setMediaInfo(info).build())
							.setResultCallback(r -> {
								if (!checkErr(r) && (this.source != null)) listener.onEnginePrepared(this);
							});
				}))).onFailure(err -> listener.onEngineError(this, err));
	}

	@Override
	public void start() {
		client.play().setResultCallback(r -> {
			if (!checkErr(r) && (source != null)) listener.onEngineStarted(this);
		});
	}

	@Override
	public void stop() {
		source = null;
		client.stop();
	}

	@Override
	public void pause() {
		client.pause();
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return completed(client.getStreamDuration());
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return completed(client.getApproximateStreamPosition());
	}

	@Override
	public void setPosition(long position) {
		client.seek(new MediaSeekOptions.Builder().setPosition(position).build());
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(speed);
	}

	@Override
	public void setSpeed(float speed) {
		this.speed = speed;
		client.setPlaybackRate(speed);
	}

	@Override
	public void setVideoView(VideoView view) {
	}

	@Override
	public float getVideoWidth() {
		return 0;
	}

	@Override
	public float getVideoHeight() {
		return 0;
	}

	@Override
	public void close() {
		listener = Listener.DUMMY;
		client.unregisterCallback(this);
	}

	public boolean isSubtitlesSupported() {
		return true;
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		List<MediaTrack> tracks = getMediaTracks();
		List<AudioStreamInfo> list = new ArrayList<>(tracks.size());
		for (MediaTrack t : tracks) {
			if (t.getType() != MediaTrack.TYPE_AUDIO) continue;
			list.add(new AudioStreamInfo(t.getId(), t.getLanguage(), t.getName()));
		}
		return list;
	}

	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		List<MediaTrack> tracks = getMediaTracks();
		List<SubtitleStreamInfo> list = new ArrayList<>(tracks.size());
		for (MediaTrack t : tracks) {
			if (t.getType() != MediaTrack.TYPE_TEXT) continue;
			list.add(new SubtitleStreamInfo(t.getId(), t.getLanguage(), t.getName()));
		}
		return completed(list);
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		long[] ids = getActiveTrackIds();
		for (AudioStreamInfo info : getAudioStreamInfo()) {
			if (contains(ids, info.getId())) return info;
		}
		return null;
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		long[] ids = getActiveTrackIds();
		for (SubtitleStreamInfo info : getSubtitleStreamInfo().getOrThrow()) {
			if (contains(ids, info.getId())) return info;
		}
		return null;
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		List<Long> ids = boxed(getActiveTrackIds());
		AudioStreamInfo cur = getCurrentAudioStreamInfo();
		if (cur != null) ids.remove(cur.getId());
		if (i != null) ids.add(i.getId());
		client.setActiveMediaTracks(unboxed(ids));
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {
		List<Long> ids = boxed(getActiveTrackIds());
		SubtitleStreamInfo cur = getCurrentSubtitleStreamInfo();
		if (cur != null) ids.remove(cur.getId());
		if (i != null) ids.add(i.getId());
		client.setActiveMediaTracks(unboxed(ids));
	}

	private List<MediaTrack> getMediaTracks() {
		MediaStatus status = client.getMediaStatus();
		if (status != null) {
			MediaInfo info = status.getMediaInfo();
			if (info != null) {
				List<MediaTrack> tracks = info.getMediaTracks();
				if (tracks != null) return tracks;
			}
		}
		return emptyList();
	}

	private long[] getActiveTrackIds() {
		MediaStatus status = client.getMediaStatus();
		if (status != null) {
			long[] ids = status.getActiveTrackIds();
			if (ids != null) return ids;
		}
		return new long[0];
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager,
																	 @Nullable AudioFocusRequestCompat audioFocusReq) {
		return true;
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager,
																@Nullable AudioFocusRequestCompat audioFocusReq) {
	}

	@Override
	public boolean adjustVolume(int direction) {
		try {
			double vol = session.getVolume();
			switch (direction) {
				case ADJUST_LOWER:
					vol -= 0.025;
					break;
				case ADJUST_RAISE:
					vol += 0.025;
					break;
				case ADJUST_TOGGLE_MUTE:
					if (vol > 0) {
						volume = vol;
						vol = 0;
					} else {
						vol = volume;
					}
					break;
				default:
					return false;
			}
			session.setVolume(vol);
			return true;
		} catch (IOException ex) {
			Log.e(ex);
		}
		return false;
	}

	@Override
	public boolean isVideoModeRequired() {
		return false;
	}

	@Override
	public void onStatusUpdated() {
		if ((client.getPlayerState() == PLAYER_STATE_IDLE) &&
				(client.getIdleReason() == IDLE_REASON_FINISHED)) {
			listener.onEngineEnded(this);
		}
	}

	@Override
	public void onMediaError(@NonNull MediaError err) {
		if (client.getPlayerState() != PLAYER_STATE_IDLE) return;
		if (source != null) listener.onEngineError(this, new MediaEngineException(err.getReason()));
	}

	private boolean checkErr(@NonNull RemoteMediaClient.MediaChannelResult r) {
		if (client.getPlayerState() != PLAYER_STATE_IDLE) return false;
		MediaError err = r.getMediaError();
		if (err == null) return false;
		if (source != null) listener.onEngineError(this, new MediaEngineException(err.getReason()));
		return true;
	}
}
