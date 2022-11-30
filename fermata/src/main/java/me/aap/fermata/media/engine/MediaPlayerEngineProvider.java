package me.aap.fermata.media.engine;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import java.util.Collections;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class MediaPlayerEngineProvider implements MediaEngineProvider {
	private Context ctx;

	@Override
	public void init(Context ctx) {
		this.ctx = ctx;
	}

	@Override
	public MediaEngine createEngine(Listener listener) {
		return new MediaPlayerEngine(ctx, listener);
	}

	@Override
	public boolean getMediaMetadata(MetadataBuilder meta, PlayableItem item) {
		MediaMetadataRetriever mmr = null;

		try {
			Uri u = item.getLocation();
			mmr = new MediaMetadataRetriever();

			if ("http".equals(u.getScheme())) mmr.setDataSource(u.toString(), Collections.emptyMap());
			else mmr.setDataSource(ctx, u);

			String m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);
			else meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getResource().getName());

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, m);

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, m);

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, m);

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, m);

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_WRITER, m);

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_GENRE, m);

			long dur = meta.getDuration();

			if (dur == 0) {
				m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

				if (m != null) {
					dur = Long.parseLong(m);
					meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur);
				}
			}

			byte[] pic = mmr.getEmbeddedPicture();
			Bitmap bm = null;

			if (pic != null) {
				bm = BitmapFactory.decodeByteArray(pic, 0, pic.length);
			}

			if ((bm == null) && item.isVideo()) {
				dur = MICROSECONDS.convert(dur, MILLISECONDS);
				bm = mmr.getFrameAtTime(dur / 2);
			}

			if (isValidBitmap(bm)) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);

			return true;
		} catch (Throwable ex) {
			Log.d(ex, "Failed to retrieve media metadata of ", item.getLocation());
			return false;
		} finally {
			if (mmr != null) {
				try {
					mmr.release();
				} catch (Exception ex) {
					Log.d(ex);
				}
			}
		}
	}

	public boolean getDuration(MetadataBuilder meta, PlayableItem item) {
		if (meta.getDuration() != 0) return true;

		MediaPlayer mp = null;
		try {
			mp = MediaPlayer.create(ctx, item.getLocation());
			if (mp == null) return false;
			meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration());
			return true;
		} catch (Throwable ex2) {
			Log.d(ex2, "Failed to retrieve duration of ", item.getLocation());
			return false;
		} finally {
			if (mp != null) mp.release();
		}
	}
}
