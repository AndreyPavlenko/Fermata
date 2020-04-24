package me.aap.fermata.engine.vlc;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.interfaces.IMedia;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.io.IoUtils;

import static org.videolan.libvlc.interfaces.IMedia.Parse.FetchLocal;
import static org.videolan.libvlc.interfaces.IMedia.Parse.FetchNetwork;
import static org.videolan.libvlc.interfaces.IMedia.Parse.ParseLocal;
import static org.videolan.libvlc.interfaces.IMedia.Parse.ParseNetwork;

/**
 * @author Andrey Pavlenko
 */
public class VlcEngineProvider implements MediaEngineProvider {
	private LibVLC vlc;
	private int audioSessionId;

	@Override
	public void init(Context ctx) {
		List<String> opts = new ArrayList<>(20);
		AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
		audioSessionId = (am != null) ? am.generateAudioSessionId() : AudioManager.ERROR;

		if (BuildConfig.DEBUG) opts.add("-vvv");
		if (audioSessionId != AudioManager.ERROR)
			opts.add("--audiotrack-session-id=" + audioSessionId);

		opts.add("--avcodec-skiploopfilter");
		opts.add("1");
		opts.add("--avcodec-skip-frame");
		opts.add("0");
		opts.add("--avcodec-skip-idct");
		opts.add("0");
		opts.add("--no-stats");
		opts.add("--android-display-chroma");
		opts.add("RV16");
		opts.add("--sout-keep");
		opts.add("--audio-time-stretch");
		opts.add("--audio-resampler");
		opts.add("soxr");
		opts.add("--subsdec-encoding=UTF8");
		opts.add("--freetype-rel-fontsize=16");
		opts.add("--freetype-color=16777215");
		opts.add("--freetype-background-opacity=0");
		opts.add("--no-sout-chromecast-audio-passthrough");
		opts.add("--sout-chromecast-conversion-quality=2");
//		opts.add("--aout=opensles,android_audiotrack");
//		opts.add("--vout=android_display");
//		opts.add("--vout=opengles2");

		vlc = new LibVLC(ctx, opts);
	}

	@Override
	public MediaEngine createEngine(MediaEngine.Listener listener) {
		return new VlcEngine(this, listener);
	}

	@Override
	public boolean getMediaMetadata(MetadataBuilder meta, PlayableItem item) {
		Media media = null;
		ParcelFileDescriptor fd = null;

		try {
			Uri uri = item.getLocation();

			if ("content".equals(uri.getScheme())) {
				ContentResolver cr = getVlc().getAppContext().getContentResolver();
				fd = cr.openFileDescriptor(uri, "r");
				media = (fd != null) ? new Media(getVlc(), fd.getFileDescriptor()) : new Media(getVlc(), uri);
			} else {
				media = new Media(getVlc(), uri);
			}

			media.parse(ParseLocal | ParseNetwork | FetchLocal | FetchNetwork);

			String m = media.getMeta(IMedia.Meta.Title);
			if ((m != null) && (fd != null) && m.startsWith("fd://")) m = null;
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);
			else meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getFile().getName());

			m = media.getMeta(IMedia.Meta.Artist);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m);

			m = media.getMeta(IMedia.Meta.AlbumArtist);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, m);

			m = media.getMeta(IMedia.Meta.Album);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m);

			m = media.getMeta(IMedia.Meta.Genre);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, m);

			m = media.getMeta(IMedia.Meta.ArtworkURL);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, m);

			meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, media.getDuration());
			return true;
		} catch (Throwable ex) {
			Log.d(getClass().getName(), "Failed to retrieve media metadata of " + item.getLocation(), ex);
			return false;
		} finally {
			if (media != null) media.release();
			IoUtils.close(fd);
		}
	}

	public LibVLC getVlc() {
		return vlc;
	}

	public int getAudioSessionId() {
		return audioSessionId;
	}

	@Override
	protected void finalize() {
		if (vlc != null) {
			vlc.release();
			vlc = null;
		}
	}
}
