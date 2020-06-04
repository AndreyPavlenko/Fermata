package me.aap.fermata.media.engine;

import android.content.Context;
import android.graphics.Bitmap;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public interface MediaEngineProvider {

	void init(Context ctx);

	MediaEngine createEngine(Listener listener);

	default boolean getMediaMetadata(MetadataBuilder meta, PlayableItem item) {
		return false;
	}

	default boolean isValidBitmap(Bitmap bm) {
		if (bm == null) return false;

		int prev = 0;

		for (int x = 0, w = bm.getWidth(), h = bm.getHeight(); x < w; x++) {
			for (int y = 0; y < h; y++) {
				int px = bm.getPixel(x, y);
				if ((px != prev) && (x != 0) && (y != 0)) return true;
				prev = px;
			}
		}

		return false;
	}
}
