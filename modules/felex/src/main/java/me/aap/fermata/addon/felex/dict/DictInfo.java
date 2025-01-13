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
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Function;
import me.aap.utils.io.Utf8LineReader;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class DictInfo {
	private static final String TAG_NAME = "#Name";
	private static final String TAG_SRC_LANG = "#SourceLang";
	private static final String TAG_TARGET_LANG = "#TargetLang";
	private static final String TAG_ACK_PHRASE = "#AckPhrase";
	private static final String TAG_SKIP_PHRASE = "#SkipPhrase";
	private static final String CHAR_MAP = "#CharMap";
	private static final String BATCH_SIZE = "#BatchSize";
	public static final int BATCH_SIZE_DEFAULT = 10;
	private static final String BATCH_TYPE = "#BatchType";
	public static final String BATCH_TYPE_RND = "rnd";
	public static final String BATCH_TYPE_LEAST = "least";
	public static final String BATCH_TYPE_MIXED = "mixed";
	private final String path;
	private final Locale sourceLang;
	private final Locale targetLang;
	private final List<String> ackPhrase;
	private final List<String> skipPhrase;
	private final List<CharMap> charMap;
	private final int batchSize;
	private final String batchType;

	public DictInfo(String path, String sourceLang, String targetLang, String ackPhrase,
									String skipPhrase,
									String charMap, int batchSize, String batchType) {
		this(path, Locale.forLanguageTag(sourceLang), Locale.forLanguageTag(targetLang), ackPhrase,
				skipPhrase,
				charMap, batchSize, batchType);
	}

	public DictInfo(String name, Locale sourceLang, Locale targetLang, String ackPhrase,
									String skipPhrase,
									String charMap, int batchSize, String batchType) {
		Function<String, List<String>> phrasesToList = (p) -> {
			if ((p == null) || (p = p.trim()).isEmpty()) return emptyList();
			return CollectionUtils.map(Arrays.asList(p.split("\\|")), (i, s, a) -> a.add(s.trim()),
					ArrayList::new);
		};
		this.path = name.trim();
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;
		this.ackPhrase = phrasesToList.apply(ackPhrase);
		this.skipPhrase = phrasesToList.apply(skipPhrase);
		this.batchSize = batchSize;
		this.batchType = batchType;

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

	private DictInfo(String path, Locale sourceLang, Locale targetLang, List<String> ackPhrase,
									 List<String> skipPhrase,
									 List<CharMap> charMap, int batchSize, String batchType) {
		this.path = path;
		this.sourceLang = sourceLang;
		this.targetLang = targetLang;
		this.ackPhrase = ackPhrase;
		this.skipPhrase = skipPhrase;
		this.charMap = charMap;
		this.batchSize = batchSize;
		this.batchType = batchType;
	}

	@Nullable
	public static DictInfo read(InputStream in) throws IOException {
		Utf8LineReader r = new Utf8LineReader(in);
		StringBuilder sb = new StringBuilder(64);
		String path = null;
		String srcLang = null;
		String targetLang = null;
		String ackPhrase = null;
		String skipPhrase = null;
		String charMap = null;
		int batchSize = BATCH_SIZE_DEFAULT;
		String batchType = BATCH_TYPE_MIXED;

		for (int i = r.readLine(sb, 1024); i != -1; sb.setLength(0), i = r.readLine(sb)) {
			if ((sb.length() == 0) || sb.charAt(0) != '#') break;
			if (TextUtils.startsWith(sb, TAG_NAME)) path = sb.substring(TAG_NAME.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SRC_LANG))
				srcLang = sb.substring(TAG_SRC_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_TARGET_LANG))
				targetLang = sb.substring(TAG_TARGET_LANG.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_ACK_PHRASE))
				ackPhrase = sb.substring(TAG_ACK_PHRASE.length()).trim();
			else if (TextUtils.startsWith(sb, TAG_SKIP_PHRASE))
				skipPhrase = sb.substring(TAG_SKIP_PHRASE.length()).trim();
			else if (TextUtils.startsWith(sb, CHAR_MAP)) charMap =
					sb.substring(CHAR_MAP.length()).trim();
			else if (TextUtils.startsWith(sb, BATCH_SIZE)) batchSize = Integer.parseInt(
					sb.substring(BATCH_SIZE.length()).trim());
			else if (TextUtils.startsWith(sb, BATCH_TYPE)) {
				var type = sb.substring(BATCH_TYPE.length()).trim();
				batchType =
						type.equalsIgnoreCase(BATCH_TYPE_RND) ?
								BATCH_TYPE_RND :
								type.equalsIgnoreCase(BATCH_TYPE_LEAST) ? BATCH_TYPE_LEAST : BATCH_TYPE_MIXED;
			}
		}

		if ((path != null) && (srcLang != null) && (targetLang != null)) {
			return new DictInfo(path, srcLang, targetLang, ackPhrase, skipPhrase, charMap, batchSize,
					batchType);
		}

		return null;
	}

	public void write(Appendable a) throws IOException {
		BiConsumer<String, List<String>> writePhrases = (tag, phrases) -> {
			if (!phrases.isEmpty()) {
				try {
					a.append(tag).append('\t');
					for (var it = phrases.iterator(); it.hasNext(); ) {
						a.append(it.next());
						if (it.hasNext()) a.append(" | ");
					}
					a.append('\n');
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		};
		a.append(TAG_NAME).append("\t\t").append(path).append('\n');
		a.append(TAG_SRC_LANG).append('\t').append(sourceLang.toLanguageTag()).append('\n');
		a.append(TAG_TARGET_LANG).append('\t').append(targetLang.toLanguageTag()).append('\n');
		writePhrases.accept(TAG_ACK_PHRASE, ackPhrase);
		writePhrases.accept(TAG_SKIP_PHRASE, skipPhrase);
		if (!charMap.isEmpty()) {
			a.append(CHAR_MAP).append('\t');
			for (var it = charMap.iterator(); it.hasNext(); ) {
				var m = it.next();
				a.append(new String(Character.toChars(m.from))).append('=').append(m.to);
				if (it.hasNext()) a.append(" | ");
			}
			a.append('\n');
		}
		a.append(BATCH_SIZE).append('\t').append(Integer.toString(batchSize)).append('\n');
		a.append(BATCH_TYPE).append('\t').append(batchType).append('\n');
		a.append('\n');
	}

	public DictInfo rename(String path) {
		return new DictInfo(path, sourceLang, targetLang, ackPhrase, skipPhrase, charMap, batchSize,
				batchType);
	}

	public String getName() {
		int idx = path.lastIndexOf('/');
		return (idx < 0) ? path : path.substring(idx + 1);
	}

	public String getPath() {
		return path;
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

	public boolean isAckPhrase(String phrase) {
		if (phrase == null) return false;
		for (var p : ackPhrase) {
			if (WordMatcher.matches(this, p, phrase)) return true;
		}
		return false;
	}

	public boolean isSkipPhrase(String phrase) {
		if (phrase == null) return false;
		for (var p : skipPhrase) {
			if (WordMatcher.matches(this, p, phrase)) return true;
		}
		return false;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public String getBatchType() {
		return batchType;
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
