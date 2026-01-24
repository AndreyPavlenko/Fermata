package me.aap.utils.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public interface TextBuilder extends CharSequence, Appendable {

	StringBuilder getStringBuilder();

	@NonNull
	TextBuilder append(@Nullable Object obj);

	@NonNull
	TextBuilder append(@Nullable String str);

	@NonNull
	TextBuilder append(@Nullable StringBuffer sb);

	@NonNull
	TextBuilder append(@Nullable CharSequence s);

	@NonNull
	TextBuilder append(@Nullable CharSequence s, int start, int end);

	@NonNull
	TextBuilder append(char[] str);

	@NonNull
	TextBuilder append(char[] str, int offset, int len);

	@NonNull
	TextBuilder append(boolean b);

	@NonNull
	TextBuilder append(char c);

	@NonNull
	TextBuilder append(int i);

	@NonNull
	TextBuilder append(long lng);

	@NonNull
	TextBuilder append(float f);

	@NonNull
	TextBuilder append(double d);

	@NonNull
	TextBuilder appendCodePoint(int codePoint);

	@NonNull
	TextBuilder delete(int start, int end);

	@NonNull
	TextBuilder deleteCharAt(int index);

	@NonNull
	TextBuilder replace(int start, int end, @NonNull String str);

	@NonNull
	TextBuilder insert(int index, char[] str, int offset, int len);

	@NonNull
	TextBuilder insert(int offset, @Nullable Object obj);

	@NonNull
	TextBuilder insert(int offset, @Nullable String str);

	@NonNull
	TextBuilder insert(int offset, char[] str);

	@NonNull
	TextBuilder insert(int dstOffset, @Nullable CharSequence s);

	@NonNull
	TextBuilder insert(int dstOffset, @Nullable CharSequence s, int start, int end);

	@NonNull
	TextBuilder insert(int offset, boolean b);

	@NonNull
	TextBuilder insert(int offset, char c);

	@NonNull
	TextBuilder insert(int offset, int i);

	@NonNull
	TextBuilder insert(int offset, long l);

	@NonNull
	TextBuilder insert(int offset, float f);

	@NonNull
	TextBuilder insert(int offset, double d);

	int indexOf(@NonNull String str);

	int indexOf(@NonNull String str, int fromIndex);

	int lastIndexOf(@NonNull String str);

	int lastIndexOf(@NonNull String str, int fromIndex);

	@NonNull
	TextBuilder reverse();

	@NonNull
	@Override
	String toString();

	void trimToSize();

	int codePointAt(int index);

	void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin);

	int length();

	void setCharAt(int index, char ch);

	@NonNull
	CharSequence subSequence(int start, int end);

	String substring(int start);

	String substring(int start, int end);

	int capacity();

	void setLength(int newLength);

	void ensureCapacity(int minimumCapacity);

	int codePointBefore(int index);

	char charAt(int index);

	int codePointCount(int beginIndex, int endIndex);

	int offsetByCodePoints(int index, int codePointOffset);

	@NonNull
	default TextBuilder clear() {
		setLength(0);
		return this;
	}
}
