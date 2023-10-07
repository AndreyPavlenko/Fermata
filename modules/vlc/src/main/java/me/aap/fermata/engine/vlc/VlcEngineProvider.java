package me.aap.fermata.engine.vlc;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaMetadataCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.interfaces.IMedia;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.security.SecurityUtils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.videolan.libvlc.interfaces.IMedia.Parse.DoInteract;
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
	private String artUri;

	@Override
	public void init(Context ctx) {
		List<String> opts = new ArrayList<>(20);
		AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
		audioSessionId = (am != null) ? am.generateAudioSessionId() : AudioManager.ERROR;

		if (BuildConfig.D) opts.add("-vvv");
		if (audioSessionId != AudioManager.ERROR) opts.add("--audiotrack-session-id=" + audioSessionId);
		opts.add("--avcodec-skip-idct");
		opts.add("0");
		opts.add("--avcodec-skip-frame");
		opts.add("0");
		opts.add("--avcodec-skiploopfilter");
		opts.add("1");
		opts.add("--android-display-chroma");
		opts.add("RV24");
		opts.add("--sout-keep");
		opts.add("--audio-time-stretch");
		opts.add("--audio-resampler");
		opts.add("soxr");
		opts.add("--subsdec-encoding=UTF8");
		opts.add("--freetype-rel-fontsize=16");
		opts.add("--freetype-color=16777215");
		opts.add("--freetype-opacity=255");
		opts.add("--freetype-background-opacity=0");
		opts.add("--freetype-shadow-color=0");
		opts.add("--freetype-shadow-opacity=128");
		opts.add("--freetype-outline-thickness=4");
		opts.add("--freetype-outline-color=0");
		opts.add("--freetype-outline-opacity=255");
		opts.add("--freetype-outline-opacity=255");
		opts.add("--freetype-rel-fontsize=16");
		opts.add("--network-caching=60000");
		opts.add("--no-lua");
		opts.add("--no-stats");

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
				media =
						(fd != null) ? new Media(getVlc(), fd.getFileDescriptor()) : new Media(getVlc(), uri);
			} else {
				media = new Media(getVlc(), uri);
			}

			media.parse(ParseLocal | ParseNetwork | FetchLocal | FetchNetwork | DoInteract);

			String title = media.getMeta(IMedia.Meta.Title);
			String artist = media.getMeta(IMedia.Meta.Artist);
			String album = media.getMeta(IMedia.Meta.Album);

			if (title != null) {
				if (!title.startsWith("vfs?resource=") && !title.startsWith("fd://")) {
					meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
				} else {
					meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getResource().getName());
				}
			} else {
				meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getResource().getName());
			}

			if (artist != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
			if (album != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);

			String m = media.getMeta(IMedia.Meta.AlbumArtist);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, m);

			m = media.getMeta(IMedia.Meta.Genre);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, m);

			long dur = media.getDuration();
			meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur);

			m = media.getMeta(IMedia.Meta.ArtworkURL);

			if (m != null) {
				if (m.startsWith("file://")) {
					meta.setImageUri(m);
				} else if (m.startsWith("attachment://")) {
					if (((artist != null) && !artist.isEmpty() && !"Unknown Artist".equals(artist)) &&
							((album != null) && !album.isEmpty() && !"Unknown Album".equals(artist))) {
						m = getArtUri() + "artistalbum/" + artist + "/" + album + "/art.png";
					} else {
						String hash = SecurityUtils.md5String(UTF_8, m, title);
						m = getArtUri() + "arturl/" + hash + "/art.png";
					}

					if (new File(new URI(m)).isFile()) {
						meta.setImageUri(m);
					}
				}
			}

			return true;
		} catch (Throwable ex) {
			Log.d(ex, "Failed to retrieve media metadata of " + item.getLocation());
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
			artUri = null;
		}
	}

	private String getArtUri() {
		if (artUri == null) {
			File dir = vlc.getAppContext().getDir("vlc", Context.MODE_PRIVATE);
			artUri = "file://" + dir.getAbsolutePath() + "/.cache/art/";
		}
		return artUri;
	}
}
