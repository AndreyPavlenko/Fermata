package me.aap.fermata.engine.exoplayer;

import android.content.Context;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.media.engine.MediaPlayerEngine;
import me.aap.fermata.media.lib.MediaLib;

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

    @Override
    public String getId() {
        return MediaPlayerEngine.ID;
    }

    @Override
    public String getEngineName() {
        return ctx.getString(me.aap.fermata.R.string.engine_exo_name);
    }

    @Override
    public boolean canPlay(MediaLib.PlayableItem i) {
        // TODO: implement
        return true;
    }

    @Override
    public MediaEngine createEngine(Listener listener) {
        return new ExoPlayerEngine(ctx, listener);
    }
}
