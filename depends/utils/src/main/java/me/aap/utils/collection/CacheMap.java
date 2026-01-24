package me.aap.utils.collection;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import me.aap.utils.app.App;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class CacheMap<K, V> implements Closeable {
	private final ConcurrentHashMap<K, Value<V>> map = new ConcurrentHashMap<>();
	private final int timeToLive;
	private final ScheduledFuture<?> timer;
	private final ScheduledExecutorService scheduler;

	public CacheMap(int timeToLive) {
		this(timeToLive, null);
	}

	public CacheMap(int timeToLive, @Nullable ScheduledExecutorService scheduler) {
		if (scheduler == null) {
			App a = App.get();
			this.scheduler = scheduler = (a != null) ? a.getScheduler() : Executors.newScheduledThreadPool(1);
		} else {
			this.scheduler = scheduler;
		}

		this.timeToLive = timeToLive;
		timer = scheduler.scheduleWithFixedDelay(this::cleanup, timeToLive, timeToLive, SECONDS);
	}

	public int size() {
		checkClosed();
		return map.size();
	}

	public boolean isEmpty() {
		checkClosed();
		return map.isEmpty();
	}

	public boolean containsKey(@NonNull K key) {
		checkClosed();
		return get(key) != null;
	}

	public boolean containsValue(@NonNull V value) {
		checkClosed();
		return map.containsValue(wrap(value));
	}

	@Nullable
	public V get(@NonNull K key) {
		checkClosed();
		return unwrap(map.get(key));
	}

	@Nullable
	public V put(K key, V value) {
		checkClosed();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			synchronized (map) {
				return unwrap(map.put(key, wrap(value)));
			}
		} else {
			return unwrap(map.put(key, wrap(value)));
		}
	}

	@Nullable
	public V remove(@NonNull K key) {
		checkClosed();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			synchronized (map) {
				return unwrap(map.remove(key));
			}
		} else {
			return unwrap(map.remove(key));
		}
	}

	public void putAll(@NonNull Map<? extends K, ? extends V> m) {
		checkClosed();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			synchronized (map) {
				CollectionUtils.forEach(m.entrySet(), e -> map.put(e.getKey(), wrap(e.getValue())));
			}
		} else {
			CollectionUtils.forEach(m.entrySet(), e -> map.put(e.getKey(), wrap(e.getValue())));
		}
	}

	public void clear() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			synchronized (map) {
				map.clear();
			}
		} else {
			map.clear();
		}
	}

	@NonNull
	public Set<K> keySet() {
		checkClosed();
		return map.keySet();
	}

	@NonNull
	public Collection<V> values() {
		checkClosed();
		return new CollectionAdapter<Value<V>, V>(map.values()) {
			@NonNull
			@Override
			public Iterator<V> iterator() {
				return new FilteringIterator<V>(super.iterator()) {
					@Override
					protected boolean accept(V v) {
						return v != null;
					}
				};
			}

			@Override
			protected V adapt(Value<V> v) {
				return unwrap(v);
			}
		};
	}

	@NonNull
	public Set<Entry<K, V>> entrySet() {
		checkClosed();
		return new SetAdapter<Entry<K, Value<V>>, Entry<K, V>>(map.entrySet()) {
			@NonNull
			@Override
			public Iterator<Entry<K, V>> iterator() {
				return new FilteringIterator<Entry<K, V>>(super.iterator()) {
					@Override
					protected boolean accept(Entry<K, V> v) {
						return v != null;
					}
				};
			}

			@Override
			protected Entry<K, V> adapt(Entry<K, Value<V>> e) {
				V v = unwrap(e.getValue());
				return (v == null) ? null : new Entry<K, V>() {
					@Override
					public K getKey() {
						return e.getKey();
					}

					@Override
					public V getValue() {
						return v;
					}

					@Override
					public V setValue(V value) {
						return unwrap(e.setValue(wrap(value)));
					}
				};
			}
		};
	}

	public void forEach(@NonNull BiConsumer<? super K, ? super V> action) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			map.forEach((k, v) -> {
				V value = unwrap(v);
				if (value != null) action.accept(k, value);
			});
		} else {
			for (Entry<K, Value<V>> e : map.entrySet()) {
				V value = unwrap(e.getValue());
				if (value != null) action.accept(e.getKey(), value);
			}
		}
	}

	public void replaceAll(@NonNull BiFunction<? super K, ? super V, ? extends V> function) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			map.replaceAll((k, v) -> {
				V value = unwrap(v);
				return (value != null) ? wrap(function.apply(k, value)) : v;
			});
		} else {
			for (Entry<K, Value<V>> e : map.entrySet()) {
				V value = unwrap(e.getValue());
				if (value != null) e.setValue(wrap(function.apply(e.getKey(), value)));
			}
		}
	}

	@Nullable
	public V putIfAbsent(K key, V value) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.putIfAbsent(key, wrap(value)));
		} else {
			synchronized (map) {
				V v = get(key);
				if (v == null) map.put(key, wrap(value));
				return v;
			}
		}
	}

	public boolean remove(@NonNull K key, @NonNull V value) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return map.remove(key, wrap(value));
		} else {
			synchronized (map) {
				return map.remove(key, wrap(value));
			}
		}
	}

	public boolean replace(@NonNull K key, @NonNull V oldValue, @NonNull V newValue) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return map.replace(key, wrap(oldValue), wrap(newValue));
		} else {
			synchronized (map) {
				return map.replace(key, wrap(oldValue), wrap(newValue));
			}
		}
	}

	@Nullable
	public V replace(@NonNull K key, @NonNull V value) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.replace(key, wrap(value)));
		} else {
			synchronized (map) {
				return unwrap(map.replace(key, wrap(value)));
			}
		}
	}

	@Nullable
	public V computeIfAbsent(K key, @NonNull Function<? super K, ? extends V> mappingFunction) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.compute(key, (k, v) -> {
				V u = unwrap(v);
				return (u == null) ? wrap(mappingFunction.apply(k)) : v;
			}));
		} else {
			synchronized (map) {
				Value<V> v = map.get(key);
				if (v == null) map.put(key, v = wrap(mappingFunction.apply(key)));
				return unwrap(v);
			}
		}
	}

	@Nullable
	public V computeIfPresent(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.compute(key, (k, v) -> {
				V u = unwrap(v);
				return (u != null) ? wrap(remappingFunction.apply(k, u)) : v;
			}));
		} else {
			synchronized (map) {
				V v = get(key);
				if (v != null) map.put(key, wrap(v = remappingFunction.apply(key, v)));
				return v;
			}
		}
	}

	@Nullable
	public V compute(K key, @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.compute(key, (k, v) -> wrap(remappingFunction.apply(k, unwrap(v)))));
		} else {
			synchronized (map) {
				V oldValue = get(key);
				V newValue = remappingFunction.apply(key, oldValue);

				if (newValue == null) {
					if (oldValue != null) remove(key);
					return null;
				} else {
					put(key, newValue);
					return newValue;
				}
			}
		}
	}

	@Nullable
	public V merge(K key, @NonNull V value, @NonNull BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		checkClosed();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return unwrap(map.merge(key, wrap(value), (v1, v2) -> wrap(remappingFunction.apply(unwrap(v1), unwrap(v2)))));
		} else {
			synchronized (map) {
				V oldValue = get(key);
				V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
				if (newValue == null) remove(key);
				else put(key, newValue);
				return newValue;
			}
		}
	}

	@Override
	public void close() {
		timer.cancel(false);
		if (scheduler != null) scheduler.shutdownNow();
		clear();
	}

	public int getTimeToLive() {
		return timeToLive;
	}

	private void checkClosed() {
		if (timer.isDone()) throw new IllegalStateException("CacheMap is closed");
	}

	private Value<V> wrap(V v) {
		return new Value<>(v);
	}

	private V unwrap(Value<V> v) {
		if (v == null) return null;
		V ref = v.get();
		if (ref == null) return null;
		if (v.ref == null) v.ref = ref;
		v.timestamp = System.currentTimeMillis();
		return v.ref;
	}

	private void cleanup() {
		cleanup(true);
	}

	private void cleanup(boolean gc) {
		long end = System.currentTimeMillis() - getTimeToLive();
		boolean hasNullRefs = false;

		for (Iterator<Entry<K, Value<V>>> it = map.entrySet().iterator(); it.hasNext(); ) {
			Entry<K, Value<V>> e = it.next();
			Value<V> v = e.getValue();

			if (v.get() == null) {
				Log.d("Removing garbage collected value: ", e);
				it.remove();
			} else if (v.ref == null) {
				hasNullRefs = true;
			} else if (v.timestamp < end) {
				Log.d("Cleaning reference: ", e);
				v.ref = null;
			}
		}

		if (gc && hasNullRefs) {
			Log.d("Calling GC to collect weak references");
			System.gc();
			cleanup(false);
		}
	}

	private static final class Value<T> extends WeakReference<T> {
		T ref;
		long timestamp = System.currentTimeMillis();

		public Value(T value) {
			super(value);
			this.ref = value;
		}

		@Override
		@SuppressWarnings({"unchecked", "EqualsWhichDoesntCheckParameterClass"})
		public boolean equals(Object o) {
			return Objects.equals(get(), ((Value<T>) o).get());
		}

		@Override
		public int hashCode() {
			return Objects.hash(get());
		}

		@NonNull
		@Override
		public String toString() {
			return String.valueOf(get());
		}
	}
}
