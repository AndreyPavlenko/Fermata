package me.aap.fermata.engine.exoplayer;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.engine.MediaEngineProvider;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class ExoPlayerEngineProvider implements MediaEngineProvider {
	private Context ctx;

	@Override
	public void init(Context ctx) {
		this.ctx = ctx;
	}

	@OptIn(markerClass = UnstableApi.class)
	@Override
	public MediaEngine createEngine(Listener listener) {
		return new ExoPlayerEngine(ctx, listener);
	}
}
