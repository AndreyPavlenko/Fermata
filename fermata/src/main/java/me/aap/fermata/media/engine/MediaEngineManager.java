package me.aap.fermata.media.engine;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public class MediaEngineManager {
	private final List<MediaEngineProvider> providers;
	private final MediaEngineProvider prefered;

	public MediaEngineManager(Context ctx) {
		this.providers = new ArrayList<>(2);
		prefered = new MediaPlayerEngineProvider();
		prefered.init(ctx);
		providers.add(prefered);

		try {
			ServiceLoader<MediaEngineProvider> l = ServiceLoader
					.load(MediaEngineProvider.class, MediaEngineProvider.class.getClassLoader());

			for (MediaEngineProvider p : l) {
				p.init(ctx);
				providers.add(p);
			}
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to load MediaEngineProviders", ex);
		}
	}

	public MediaEngine createEngine(MediaEngine current, PlayableItem i, Listener listener) {
		if (current != null) {
			if (current.canPlay(i)) {
				return current;
			} else {
				current.close();
			}
		}

		String id = i.getPrefs().getMediaEngineIdPref();
		if ((id == null) && prefered.canPlay(i)) return prefered.createEngine(listener);

		if (id != null) {
			for (MediaEngineProvider p : providers) {
				if (id.equals(p.getId()) && p.canPlay(i)) return p.createEngine(listener);
			}
		}

		for (MediaEngineProvider p : providers) {
			if (p.canPlay(i)) return p.createEngine(listener);
		}

		return null;
	}
}
