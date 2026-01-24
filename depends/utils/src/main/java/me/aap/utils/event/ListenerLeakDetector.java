package me.aap.utils.event;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.utils.BuildConfig;
import me.aap.utils.function.BiFunction;
import me.aap.utils.log.Log;

import static me.aap.utils.collection.CollectionUtils.putIfAbsent;

/**
 * @author Andrey Pavlenko
 */
public class ListenerLeakDetector {
	private static final Map<Key, Throwable> map = (BuildConfig.D) ?
			new ConcurrentHashMap<>() : Collections.emptyMap();

	public static void add(@NonNull Object broadcaster, @NonNull Object listener) {
		if (!BuildConfig.D) return;
		Throwable t = putIfAbsent(map, new Key(broadcaster, listener), new Throwable());
		if (t != null) {
			throw new IllegalArgumentException("Listener " + listener
					+ " is already registered to " + broadcaster, t);
		}
	}

	public static void remove(@NonNull Object broadcaster, @NonNull Object listener) {
		if (!BuildConfig.D) return;
		if (map.remove(new Key(broadcaster, listener)) == null) {
			Log.d(new IllegalArgumentException(), "Listener " + listener
					+ " is not registered to " + broadcaster);
		}
	}

	public static boolean hasLeaks() {
		if (!BuildConfig.D) return false;
		return hasLeaks((b, l) -> true);
	}

	public static boolean hasLeaks(BiFunction<Object, Object, Boolean> test) {
		if (!BuildConfig.D) return false;
		if (map.isEmpty()) return false;

		boolean hasLeaks = false;
		for (Map.Entry<Key, Throwable> e : map.entrySet()) {
			Key k = e.getKey();
			if (test.apply(k.broadcaster, k.listener)) {
				hasLeaks = true;
				Log.d(new Throwable(e.getValue()), "Listener " + k.listener
						+ " has not been unregistered from " + k.broadcaster);
			}
		}
		return hasLeaks;
	}

	private static final class Key {
		@NonNull
		final Object broadcaster;
		@NonNull
		final Object listener;

		Key(@NonNull Object broadcaster, @NonNull Object listener) {
			this.broadcaster = broadcaster;
			this.listener = listener;
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object o) {
			Key key = (Key) o;
			return broadcaster.equals(key.broadcaster) && listener.equals(key.listener);
		}

		@Override
		public int hashCode() {
			return Objects.hash(broadcaster, listener);
		}
	}
}
