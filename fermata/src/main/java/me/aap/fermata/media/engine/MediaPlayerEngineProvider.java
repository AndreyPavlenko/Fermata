package me.aap.fermata.media.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

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

	public boolean getMediaMetadata(MediaMetadataCompat.Builder meta, PlayableItem item) {
		MediaMetadataRetriever mmr = null;

		try {
			mmr = new MediaMetadataRetriever();
			mmr.setDataSource(ctx, item.getLocation());

			String m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
			if (m != null) meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, m);
			else meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getFile().getName());

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

			m = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
			if (m != null)
				meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Long.parseLong(m));

			byte [] pic = mmr.getEmbeddedPicture();

			if(pic != null) {
				Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length);
				if(bm != null) meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bm);
			}
		} catch (Exception ex) {
			Log.d(getClass().getName(), "Failed to retrieve media metadata of " + item.getLocation(), ex);

			MediaPlayer mp = null;
			try {
				mp = MediaPlayer.create(ctx, item.getLocation());
				if (mp == null) return false;

				meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mp.getDuration());
				meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getFile().getName());
			} catch (Exception ex2) {
				Log.d(getClass().getName(), "Failed to retrieve duration of " + item.getLocation(), ex2);
				return false;
			} finally {
				if (mp != null) mp.release();
			}
		} finally {
			if (mmr != null) mmr.release();
		}

		return true;
	}
}
