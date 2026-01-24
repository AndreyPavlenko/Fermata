package me.aap.utils.text;

import static me.aap.utils.misc.Assert.assertSame;
import static me.aap.utils.misc.Assert.assertTrue;
import static me.aap.utils.os.OsUtils.isAndroid;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.BuildConfig;
import me.aap.utils.concurrent.PooledThread;
import me.aap.utils.function.Function;

/**
 * @author Andrey Pavlenko
 */
public class SharedTextBuilder implements TextBuilder, AutoCloseable {
	private final Thread thread;
	private final StringBuilder sb = new StringBuilder();
	private boolean inUse;
	private Throwable usedBy;

	private SharedTextBuilder() {
		this(Thread.currentThread());
	}

	private SharedTextBuilder(Thread thread) {
		this.thread = thread;
	}

	public static SharedTextBuilder create(Thread thread) {
		return new SharedTextBuilder(thread);
	}

	public static SharedTextBuilder get() {
		return get(0);
	}

	public static <T> T apply(Function<SharedTextBuilder, T> f) {
		try (SharedTextBuilder tb = get()) {
			return f.apply(tb);
		}
	}

	public static SharedTextBuilder get(int minCapacity) {
		Thread t = Thread.currentThread();
		SharedTextBuilder sb;

		if (t instanceof PooledThread) {
			sb = ((PooledThread) t).getSharedTextBuilder();
		} else if (isAndroid() && (t == Looper.getMainLooper().getThread())) {
			sb = MainThreadBuilder.instance;
		} else {
			sb = new SharedTextBuilder();
		}

		if (sb.inUse) {
			if (BuildConfig.D) {
				new AssertionError("SharedStringBuilder is in use", sb.usedBy).printStackTrace();
			}

			sb = create(t);
			sb.inUse = true;
		} else {
			sb.inUse = true;
			if (BuildConfig.D) sb.usedBy = new Throwable("Used by");
		}

		if (minCapacity > 0) sb.sb.ensureCapacity(minCapacity);
		sb.sb.setLength(0);
		return sb;
	}

	public String releaseString() {
		assertTrue(inUse);
		inUse = false;
		if (BuildConfig.D) usedBy = null;
		return sb.toString();
	}

	public void release() {
		assertTrue(inUse);
		if (BuildConfig.D) usedBy = null;
		inUse = false;
	}

	@Override
	public void close() {
		release();
	}

	@Override
	public StringBuilder getStringBuilder() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb;
	}

	@NonNull
	public SharedTextBuilder append(@Nullable Object obj) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(obj);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(@Nullable String str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(str);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(@Nullable StringBuffer sb) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		this.sb.append(sb);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(@Nullable CharSequence s) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(s);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(@Nullable CharSequence s, int start, int end) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(s, start, end);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(char[] str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(str);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(char[] str, int offset, int len) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(str, offset, len);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(boolean b) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(b);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(char c) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(c);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(int i) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(i);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(long lng) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(lng);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(float f) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(f);
		return this;
	}

	@NonNull
	public SharedTextBuilder append(double d) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.append(d);
		return this;
	}

	@NonNull
	public SharedTextBuilder appendCodePoint(int codePoint) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.appendCodePoint(codePoint);
		return this;
	}

	@NonNull
	public SharedTextBuilder delete(int start, int end) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.delete(start, end);
		return this;
	}

	@NonNull
	public SharedTextBuilder deleteCharAt(int index) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.deleteCharAt(index);
		return this;
	}

	@NonNull
	public SharedTextBuilder replace(int start, int end, @androidx.annotation.NonNull String str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.replace(start, end, str);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int index, char[] str, int offset, int len) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(index, str, offset, len);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, @Nullable Object obj) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, obj);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, @Nullable String str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, str);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, char[] str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, str);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int dstOffset, @Nullable CharSequence s) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(dstOffset, s);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int dstOffset, @Nullable CharSequence s, int start, int end) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(dstOffset, s, start, end);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, boolean b) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, b);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, char c) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, c);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, int i) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, i);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, long l) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, l);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, float f) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, f);
		return this;
	}

	@NonNull
	public SharedTextBuilder insert(int offset, double d) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.insert(offset, d);
		return this;
	}

	public int indexOf(@androidx.annotation.NonNull String str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.indexOf(str);
	}

	public int indexOf(@androidx.annotation.NonNull String str, int fromIndex) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.indexOf(str, fromIndex);
	}

	public int lastIndexOf(@androidx.annotation.NonNull String str) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.lastIndexOf(str);
	}

	public int lastIndexOf(@androidx.annotation.NonNull String str, int fromIndex) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.lastIndexOf(str, fromIndex);
	}

	@NonNull
	public SharedTextBuilder reverse() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.reverse();
		return this;
	}

	@androidx.annotation.NonNull
	@Override
	public String toString() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.toString();
	}

	public void trimToSize() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.trimToSize();
	}

	public int codePointAt(int index) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.codePointAt(index);
	}

	public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.getChars(srcBegin, srcEnd, dst, dstBegin);
	}

	public int length() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.length();
	}

	public void setCharAt(int index, char ch) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.setCharAt(index, ch);
	}

	@NonNull
	public CharSequence subSequence(int start, int end) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.subSequence(start, end);
	}

	public String substring(int start) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.substring(start);
	}

	public String substring(int start, int end) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.substring(start, end);
	}

	public int capacity() {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.capacity();
	}

	public void setLength(int newLength) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.setLength(newLength);
	}

	public void ensureCapacity(int minimumCapacity) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		sb.ensureCapacity(minimumCapacity);
	}

	public int codePointBefore(int index) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.codePointBefore(index);
	}

	public char charAt(int index) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.charAt(index);
	}

	public int codePointCount(int beginIndex, int endIndex) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.codePointCount(beginIndex, endIndex);
	}

	public int offsetByCodePoints(int index, int codePointOffset) {
		assertTrue(inUse);
		assertSame(thread, Thread.currentThread());
		return sb.offsetByCodePoints(index, codePointOffset);
	}

	private interface MainThreadBuilder {
		SharedTextBuilder instance = new SharedTextBuilder();
	}
}
