package me.aap.utils.text;


import static java.util.Collections.emptyMap;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("StatementWithEmptyBody")
public class PatternCompat {
	private static final java.util.regex.Pattern NAMED_GROUP = Pattern.compile("\\(\\?<(\\w\\w*)>");
	private final Pattern pattern;

	private PatternCompat(Pattern pattern) {
		this.pattern = pattern;
	}

	public Pattern getPattern() {
		return pattern;
	}

	public Matcher matcher(CharSequence s) {
		return getPattern().matcher(s);
	}

	public String group(Matcher m, String g) {
		return m.group(g);
	}

	public static PatternCompat compile(CharSequence regex) {
		return compile(regex, 0);
	}

	public static PatternCompat compile(CharSequence regex, int f) {
		String s = regex.toString();
		Pattern p = Pattern.compile(s, f);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new PatternCompat(p);

		Matcher m = NAMED_GROUP.matcher(s);
		Map<String, Integer> groups = new HashMap<>();
		while (m.find()) {
			int idx = m.start();
			if (!isEscaped(s, idx)) groups.put(m.group(1), groupIdx(s, idx));
		}
		return new NamedGroupsPattern(p, groups.isEmpty() ? emptyMap() : groups);
	}

	private static boolean isEscaped(String s, int idx) {
		return isSlashEscaped(s, idx) || isQuoteEscaped(s, idx);
	}

	private static boolean isSlashEscaped(String s, int idx) {
		int n = 0;
		for (; (idx > 0) && (s.charAt(idx - 1) == '\\'); idx--, n++) ;
		return (n & 1) == 1;
	}

	private static boolean isQuoteEscaped(String s, int idx) {
		if (idx < 2) return false;
		for (int qidx = s.lastIndexOf("\\Q", idx - 1); qidx >= 0; qidx = s.lastIndexOf("\\Q", qidx - 1)) {
			if (!isSlashEscaped(s, qidx)) return s.indexOf("\\E", qidx + 1) < 0;
		}
		return false;
	}

	private static int groupIdx(String s, int idx) {
		int g = 1;
		for (int pidx = s.lastIndexOf('(', idx - 1); pidx >= 0; pidx = s.lastIndexOf('(', pidx - 1)) {
			if (!isEscaped(s, pidx) && !isCharClass(s, pidx)) g++;
		}
		return g;
	}

	static private boolean isCharClass(String s, int idx) {
		int oidx = s.lastIndexOf('[', idx - 1);
		for (; (oidx >= 0) && isEscaped(s, oidx); oidx = s.lastIndexOf('[', oidx - 1)) ;
		if (oidx < 0) return false;

		int cidx = s.indexOf(']', oidx + 1);
		for (; (cidx >= 0) && isEscaped(s, cidx); cidx = s.indexOf(']', cidx + 1)) ;
		return (cidx > idx);
	}

	private static final class NamedGroupsPattern extends PatternCompat {
		private final Map<String, Integer> namedGroups;

		private NamedGroupsPattern(Pattern pattern, Map<String, Integer> namedGroups) {
			super(pattern);
			this.namedGroups = namedGroups;
		}

		public String group(Matcher m, String g) {
			Integer id = namedGroups.get(g);
			return (id == null) ? null : m.group(id);
		}
	}
}
