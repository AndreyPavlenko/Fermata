package me.aap.fermata.addon.felex.dict;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import me.aap.utils.io.Utf8LineReader;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class DictInfo {
	private static final String TAG_NAME = "#Name";
	private static final String TAG_SRC_LANG = "#SourceLang";
	private static final String TAG_TARGET_LANG = "#TargetLang";
	private static final String TAG_SKIP_PHRASE = "#SkipPhrase";
	private final String name;
	private final Locale sourceLang;
	private final Locale targetLang;
	private final String skipPhrase;

	public DictInfo(String name, String sourceLang, String targetLang, String skipPhrase) {
		this(name, Locale.forLanguageTag(sourceLang), Locale.forLanguageTag(targetLang), skipPhrase);
	}

	public DictInfo(String name, Locale sourceLang, Locale targetLang, String skipPhrase) {
		this.name = name.trim();
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;
		this.skipPhrase = (skipPhrase == null) ? "" : skipPhrase.trim();
	}

	@Nullable
	public static DictInfo read(InputStream in) throws IOException {
		Utf8LineReader r = new Utf8LineReader(in);
		StringBuilder sb = new StringBuilder(64);
		String name = null;
		String srcLang = null;
		String targetLang = null;
		String skipPhrase = null;

		for (int i = r.readLine(sb, 1024); i != -1; sb.setLength(0), i = r.readLine(sb)) {
			if ((sb.length() == 0) || sb.charAt(0) != '#') break;
			if (TextUtils.startsWith(sb, TAG_NAME)) name = sb.substring(TAG_NAME.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SRC_LANG))
				srcLang = sb.substring(TAG_SRC_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_TARGET_LANG))
				targetLang = sb.substring(TAG_TARGET_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SKIP_PHRASE))
				skipPhrase = sb.substring(TAG_SKIP_PHRASE.length()).trim();
		}

		if ((name != null) && (srcLang != null) && (targetLang != null)) {
			return new DictInfo(name, srcLang, targetLang, skipPhrase);
		}

		return null;
	}

	public void write(Appendable a) throws IOException {
		a.append(TAG_NAME).append("\t\t").append(getName()).append('\n');
		a.append(TAG_SRC_LANG).append("\t").append(getSourceLang().toLanguageTag()).append('\n');
		a.append(TAG_TARGET_LANG).append("\t").append(getTargetLang().toLanguageTag()).append("\n");
		a.append(TAG_SKIP_PHRASE).append("\t").append(getSkipPhrase()).append("\n\n");
	}

	public String getName() {
		return name;
	}

	public Locale getSourceLang() {
		return sourceLang;
	}

	public Locale getTargetLang() {
		return targetLang;
	}

	public String getSkipPhrase() {
		return skipPhrase;
	}

	@NonNull
	@Override
	public String toString() {
		return getName() + " (" + getSourceLang().getLanguage().toUpperCase() + '-' +
				getTargetLang().getLanguage().toUpperCase() + ')';
	}
}
