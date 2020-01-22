package me.aap.fermata.media.engine;

import android.content.Context;

import me.aap.fermata.media.engine.MediaEngine.Listener;

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
}
