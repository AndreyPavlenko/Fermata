package me.aap.utils.collection;

import static java.util.Objects.requireNonNull;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntBiConsumer;
import me.aap.utils.function.IntFunction;
import me.aap.utils.function.Predicate;
import me.aap.utils.function.ToIntFunction;

/**
 * @author Andrey Pavlenko
 */
public class CollectionUtils {

	public static <T> int indexOf(T[] array, T value) {
		for (int i = 0; i < array.length; i++) {
			if (Objects.equals(value, array[i])) return i;
		}
		return -1;
	}

	public static int indexOf(int[] array, int value) {
		for (int i = 0; i < array.length; i++) {
			if (value == array[i]) return i;
		}
		return -1;
	}

	public static int indexOf(long[] array, long value) {
		for (int i = 0; i < array.length; i++) {
			if (value == array[i]) return i;
		}
		return -1;
	}

	public static <T> int indexOf(Iterable<T> i, Predicate<T> predicate) {
		int idx = 0;
		for (T t : i) {
			if (predicate.test(t)) return idx;
			idx++;
		}
		return -1;
	}

	public static boolean contains(int[] array, int value) {
		return indexOf(array, value) != -1;
	}

	public static boolean contains(long[] array, long value) {
		return indexOf(array, value) != -1;
	}

	public static <T> boolean contains(T[] array, T value) {
		return indexOf(array, value) != -1;
	}

	public static <T> boolean contains(Iterable<T> i, Predicate<T> predicate) {
		for (T t : i) {
			if (predicate.test(t)) return true;
		}
		return false;
	}

	public static <T> T find(Iterable<T> i, Predicate<T> predicate) {
		for (T t : i) {
			if (predicate.test(t)) return t;
		}
		return null;
	}

	public static <T> boolean remove(Iterable<T> i, Predicate<T> predicate) {
		for (Iterator<T> it = i.iterator(); it.hasNext(); ) {
			if (predicate.test(it.next())) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	public static <T> T[] remove(T[] array, int idx) {
		Class<?> type = requireNonNull(array.getClass().getComponentType());
		@SuppressWarnings("unchecked") T[] a = (T[]) Array.newInstance(type, array.length - 1);
		System.arraycopy(array, 0, a, 0, idx);
		if (idx != (array.length - 1)) System.arraycopy(array, idx, a, idx + 1, a.length - idx);
		return a;
	}

	public static <T> void replace(List<T> list, T o, T with) {
		int i = list.indexOf(o);
		if (i != -1) list.set(i, with);
	}

	public static <T> void move(List<T> list, int fromPosition, int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				Collections.swap(list, i, i + 1);
			}
		} else {
			for (int i = fromPosition; i > toPosition; i--) {
				Collections.swap(list, i, i - 1);
			}
		}
	}

	public static <T> void move(T[] array, int fromPosition, int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				T t = array[i];
				array[i] = array[i + 1];
				array[i + 1] = t;
			}
		} else {
			for (int i = fromPosition; i > toPosition; i--) {
				T t = array[i];
				array[i] = array[i - 1];
				array[i - 1] = t;
			}
		}
	}

	@Nullable
	public static <K, V> V putIfAbsent(Map<K, V> m, K key, V value) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return m.putIfAbsent(key, value);
		} else {
			V v = m.get(key);
			if (v != null) return v;
			m.put(key, value);
			return null;
		}
	}

	public static <K, V> V computeIfAbsent(Map<K, V> m, K key, Function<? super K, ? extends V> f) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return m.computeIfAbsent(key, f::apply);
		} else {
			V v = m.get(key);
			if (v != null) return v;
			m.put(key, v = f.apply(key));
			return v;
		}
	}

	public static <K, V> V compute(Map<K, V> m, K key,
																 @NonNull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return m.compute(key, remappingFunction::apply);
		} else {
			V oldValue = m.get(key);
			V newValue = remappingFunction.apply(key, oldValue);

			if (newValue == null) {
				if (oldValue != null) m.remove(key);
				return null;
			} else {
				m.put(key, newValue);
				return newValue;
			}
		}
	}

	public static <K, V> V getOrDefault(Map<K, V> m, K key, V defaultValue) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return m.getOrDefault(key, defaultValue);
		} else {
			V v;
			return (((v = m.get(key)) != null) || m.containsKey(key)) ? v : defaultValue;
		}
	}

	public static boolean remove(Map<?, ?> m, Object key, Object value) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return m.remove(key, value);
		} else if (Objects.equals(m.get(key), value)) {
			m.remove(key);
			return true;
		} else {
			return false;
		}
	}

	public static <T, R> R[] mapToArray(Collection<? extends T> collection,
																			Function<? super T, ? extends R> mapper,
																			IntFunction<R[]> generator) {
		return map(collection, (i, t, a) -> a[i] = mapper.apply(t), generator);
	}

	public static <T, R> List<R> map(Collection<? extends T> collection,
																	 Function<? super T, R> mapper) {
		var a = new ArrayList<R>(collection.size());
		for (var e : collection) a.add(mapper.apply(e));
		return a;
	}

	public static <T, R> R map(Collection<? extends T> collection,
														 IntBiConsumer<? super T, R> mapper,
														 IntFunction<R> generator) {
		return filterMap(collection, t -> true, mapper, generator);
	}

	public static <T> List<T> filter(Collection<T> list, Predicate<? super T> predicate) {
		return filter(list, predicate, ArrayList::new);
	}

	public static <T, C1 extends Collection<T>, C2 extends Collection<T>> C2 filter(C1 collection,
																																									Predicate<?
																																											super T> predicate,
																																									IntFunction<C2> generator) {
		return filterMap(collection, predicate, t -> t, generator);
	}

	public static <T, R> List<R> filterMap(List<? extends T> list, Predicate<? super T> predicate,
																				 Function<? super T, R> mapper) {
		return filterMap(list, predicate, mapper, ArrayList::new);
	}

	public static <T, R> R filterMap(Collection<? extends T> collection,
																	 Predicate<? super T> predicate,
																	 IntBiConsumer<? super T, R> mapper,
																	 IntFunction<R> generator) {
		int size = collection.size();
		R a = generator.apply(size);
		int i = 0;

		for (T t : collection) {
			if (predicate.test(t)) mapper.accept(i++, t, a);
		}

		return a;
	}

	public static <T> LinkedHashSet<T> newLinkedHashSet(int size) {
		return new LinkedHashSet<>((int) (size / 0.75 + 1));
	}

	public static <T> void addAll(Collection<T> c, @Nullable T[] values) {
		if (values == null) return;
		Collections.addAll(c, values);
	}

	public static <T, R, TC extends Collection<? extends T>, RC extends Collection<R>>
	RC filterMap(TC collection, Predicate<? super T> predicate, Function<? super T, R> mapper, IntFunction<RC> generator) {
		int size = collection.size();
		RC mapped = generator.apply(size);
		for (T t : collection) {
			if (predicate.test(t)) mapped.add(mapper.apply(t));
		}
		return mapped;
	}

	public static <T> void forEach(Iterable<T> it, Consumer<? super T> action) {
		for (T t : it) {
			action.accept(t);
		}
	}

	public static <T> int binarySearch(List<? extends T> l, ToIntFunction<T> comparator) {
		int low = 0;
		int high = l.size() - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			int cmp = comparator.applyAsInt(l.get(mid));
			if (cmp < 0) low = mid + 1;
			else if (cmp > 0) high = mid - 1;
			else return mid;
		}

		return -(low + 1);
	}

	public static <T extends Comparable<T>> T[] sort(T[] array) {
		Arrays.sort(array);
		return array;
	}

	@SuppressWarnings("ComparatorCombinators")
	public static <T, U extends Comparable<? super U>> Comparator<T> comparing(
			Function<? super T, ? extends U> k) {
		return (c1, c2) -> k.apply(c1).compareTo(k.apply(c2));
	}

	@SuppressWarnings("ComparatorCombinators")
	public static <T> Comparator<T> comparingInt(ToIntFunction<? super T> k) {
		return (c1, c2) -> Integer.compare(k.applyAsInt(c1), k.applyAsInt(c2));
	}

	public static List<Long> boxed(long[] array) {
		List<Long> list = new ArrayList<>(array.length);
		for (long v : array) list.add(v);
		return list;
	}

	public static long[] unboxed(List<Long> list) {
		long[] array = new long[list.size()];
		for (int i = 0, n = list.size(); i < n; i++) array[i] = list.get(i);
		return array;
	}
}
