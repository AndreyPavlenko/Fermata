package me.aap.fermata.addon.felex.dict;

import static java.util.Collections.emptyList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.utils.collection.CollectionUtils;
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
	private static final String CHAR_MAP = "#CharMap";
	private final String name;
	private final Locale sourceLang;
	private final Locale targetLang;
	private final List<String> skipPhrase;
	private final List<CharMap> charMap;

	public DictInfo(String name, String sourceLang, String targetLang, String skipPhrase,
									String charMap) {
		this(name, Locale.forLanguageTag(sourceLang), Locale.forLanguageTag(targetLang), skipPhrase,
				charMap);
	}

	public DictInfo(String name, Locale sourceLang, Locale targetLang, String skipPhrase,
									String charMap) {
		this.name = name.trim();
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;

		if ((skipPhrase == null) || (skipPhrase = skipPhrase.trim()).isEmpty()) {
			this.skipPhrase = emptyList();
		} else {
			this.skipPhrase =
					CollectionUtils.map(Arrays.asList(skipPhrase.split("\\|")), (i, p, a) -> a.add(p.trim()),
							ArrayList::new);
		}
		if ((charMap == null) || (charMap = charMap.trim()).isEmpty()) {
			this.charMap = new ArrayList<>();
		} else {
			this.charMap = CollectionUtils.map(Arrays.asList(charMap.split("\\|")), (i, p, a) -> {
				var s = p.split("=");
				if (s.length != 2) return;
				s[0] = s[0].trim().toLowerCase();
				s[1] = s[1].trim().toLowerCase();
				if (s[0].isEmpty() || s[1].isEmpty()) return;
				a.add(new CharMap(s[0].codePointAt(0), s[1]));
			}, ArrayList::new);
		}
		for (var lang : new String[]{sourceLang.getLanguage().toLowerCase(),
				targetLang.getLanguage().toLowerCase()}) {
			for (var m : getCharMap(lang)) {
				if (!this.charMap.contains(m)) this.charMap.add(m);
			}
		}
	}

	private DictInfo(String name, Locale sourceLang, Locale targetLang, List<String> skipPhrase,
									 List<CharMap> charMap) {
		this.name = name;
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;
		this.skipPhrase = skipPhrase;
		this.charMap = charMap;
	}

	@Nullable
	public static DictInfo read(InputStream in) throws IOException {
		Utf8LineReader r = new Utf8LineReader(in);
		StringBuilder sb = new StringBuilder(64);
		String name = null;
		String srcLang = null;
		String targetLang = null;
		String skipPhrase = null;
		String charMap = null;

		for (int i = r.readLine(sb, 1024); i != -1; sb.setLength(0), i = r.readLine(sb)) {
			if ((sb.length() == 0) || sb.charAt(0) != '#') break;
			if (TextUtils.startsWith(sb, TAG_NAME)) name = sb.substring(TAG_NAME.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SRC_LANG))
				srcLang = sb.substring(TAG_SRC_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_TARGET_LANG))
				targetLang = sb.substring(TAG_TARGET_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SKIP_PHRASE))
				skipPhrase = sb.substring(TAG_SKIP_PHRASE.length()).trim();
			else if (TextUtils.startsWith(sb, CHAR_MAP)) charMap =
					sb.substring(CHAR_MAP.length()).trim();
		}

		if ((name != null) && (srcLang != null) && (targetLang != null)) {
			return new DictInfo(name, srcLang, targetLang, skipPhrase, charMap);
		}

		return null;
	}

	public void write(Appendable a) throws IOException {
		a.append(TAG_NAME).append("\t\t").append(name).append('\n');
		a.append(TAG_SRC_LANG).append('\t').append(sourceLang.toLanguageTag()).append('\n');
		a.append(TAG_TARGET_LANG).append('\t').append(targetLang.toLanguageTag()).append('\n');
		if (!skipPhrase.isEmpty()) {
			a.append(TAG_SKIP_PHRASE).append('\t');
			for (var it = skipPhrase.iterator(); it.hasNext(); ) {
				a.append(it.next());
				if (it.hasNext()) a.append(" | ");
			}
			a.append('\n');
		}
		if (!charMap.isEmpty()) {
			a.append(CHAR_MAP).append('\t');
			for (var it = charMap.iterator(); it.hasNext(); ) {
				var m = it.next();
				a.append(new String(Character.toChars(m.from))).append('=').append(m.to);
				if (it.hasNext()) a.append(" | ");
			}
			a.append('\n');
		}
		a.append('\n');
	}

	public DictInfo rename(String name) {
		return new DictInfo(name, sourceLang, targetLang, skipPhrase, charMap);
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

	@Nullable
	public String getCharMap(int lowerCaseChar) {
		for (var m : charMap) {
			if (m.from == lowerCaseChar) return m.to;
		}
		return null;
	}

	public boolean isSkipPhrase(String phrase) {
		if (phrase == null) return false;
		for (var p : skipPhrase) {
			if (WordMatcher.matches(this, p, phrase)) return true;
		}
		return false;
	}

	@NonNull
	@Override
	public String toString() {
		return getName() + " (" + sourceLang.getLanguage().toUpperCase() + '-' +
				targetLang.getLanguage().toUpperCase() + ')';
	}

	private static List<CharMap> getCharMap(String lang) {
		return switch (lang) {
			case "es" -> Collections.singletonList(new CharMap('ñ', "n"));
			case "de" -> Collections.singletonList(new CharMap('ß', "ss"));
			case "ru" -> Collections.singletonList(new CharMap('ё', "е"));
			case "fr" -> Arrays.asList(
					new CharMap('é', "e"),
					new CharMap('è', "e"),
					new CharMap('ê', "e"),
					new CharMap('ë', "e"),
					new CharMap('ç', "c")
			);
			case "it" -> Arrays.asList(
					new CharMap('à', "a"),
					new CharMap('á', "a"),
					new CharMap('â', "a"),
					new CharMap('ä', "a"),
					new CharMap('è', "e"),
					new CharMap('é', "e"),
					new CharMap('ê', "e"),
					new CharMap('ë', "e")
			);
			default -> Collections.emptyList();
		};
	}

	private record CharMap(int from, String to) {}
}
