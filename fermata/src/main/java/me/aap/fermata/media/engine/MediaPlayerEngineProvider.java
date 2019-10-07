package me.aap.fermata.media.engine;

import android.content.Context;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib;

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
	public String getId() {
		return MediaPlayerEngine.ID;
	}

	@Override
	public String getEngineName() {
		return ctx.getString(R.string.engine_mp_name);
	}

	@Override
	public boolean canPlay(MediaLib.PlayableItem i) {
		return true;
	}

	@Override
	public MediaEngine createEngine(Listener listener) {
		return new MediaPlayerEngine(ctx, listener);
	}
}
