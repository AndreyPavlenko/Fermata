package me.aap.fermata.addon.felex.dict;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.MemOutputStream;

/**
 * @author Andrey Pavlenko
 */
public class Word implements Comparable<Word> {
	private final String raw;
	private long offset;
	private long cacheOffset;
	private int cacheLen;
	private byte dirProgress;
	private byte revProgress;

	private Word(String raw, long offset) {
		this.raw = raw;
		this.offset = offset;
	}

	static Word create(CharSequence word) {
		return create(new StringBuilder(word), 0);
	}

	static Word create(StringBuilder sb, long offset) {
		String raw = sb.toString().trim();
		int i = raw.indexOf('[');
		if (i == -1) return new Word(raw, offset);

		boolean skip = true;
		int expr = 0;
		int word = raw.length();
		sb.setLength(word);
		for (; expr < i; expr++) sb.setCharAt(expr, raw.charAt(expr));

		for (; i < word; i++) {
			char c = raw.charAt(i);
			if (c == '[') {
				skip = true;
			} else if (c == ']') {
				skip = false;
			} else {
				sb.setCharAt(expr++, c);
				if (!skip) sb.append(c);
			}
		}

		for (; (word < sb.length()) && sb.charAt(word) <= ' '; word++) ;
		for (; (sb.length() > 0) && sb.charAt(sb.length() - 1) <= ' '; sb.setLength(sb.length() - 1)) ;
		return new Expr(raw, offset, sb.substring(word), sb.substring(0, expr));
	}

	public String getWord() {
		return raw;
	}

	public String getExpr() {
		return raw;
	}

	public int getDirProgress() {
		checkProg(dirProgress);
		return dirProgress;
	}

	public int getRevProgress() {
		checkProg(revProgress);
		return revProgress;
	}

	public void incrProgress(Dict dict, boolean dir, int diff) {
		if (diff == 0) return;
		int prog = (dir ? dirProgress : revProgress);
		int newProg = prog + diff;
		newProg = (newProg < 0) ? 0 : Math.min(newProg, 100);
		diff = (byte) (newProg - prog);
		if (diff == 0) return;
		if (dir) dirProgress = (byte) newProg;
		else revProgress = (byte) newProg;
		dict.incrProgress(this, dir, diff);
	}

	void wipeProgress() {
		dirProgress = revProgress = 0;
	}

	private static void checkProg(int prog) {
		assert (prog >= 0);
		assert (prog <= 100);
	}

	public int getProgress() {
		return (getDirProgress() + getRevProgress()) / 2;
	}

	public FutureSupplier<List<Translation>> getTranslations(Dict dict) {
		return dict.readTranslations(this);
	}

	public FutureSupplier<Void> setTranslations(Dict dict, List<Translation> translations) {
		return dict.writeTranslations(this, translations);
	}

	public boolean matches(String w) {
		String word = getWord();
		if (word.equalsIgnoreCase(w)) return true;
		String expr = getExpr();
		//noinspection StringEquality
		return (expr != word) && expr.equalsIgnoreCase(w);
	}

	@Override
	public int compareTo(Word w) {
		int c = getWord().compareToIgnoreCase(w.getWord());
		return (c == 0) ? Long.compare(w.getOffset(), getOffset()) : c;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		return (o instanceof Word) && getWord().equals(((Word) o).getWord());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getWord());
	}

	@NonNull
	@Override
	public String toString() {
		return getWord();
	}

	public ByteBuffer toBytes(List<Translation> translations) {
		MemOutputStream m = new MemOutputStream(512);
		try (Writer w = new OutputStreamWriter(m, UTF_8)) {
			write(w, translations);
			w.append('\n');
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		return ByteBuffer.wrap(m.getBuffer(), 0, m.getCount());
	}

	public void write(Appendable a, List<Translation> translations) throws IOException {
		a.append(raw).append('\n');
		for (Translation t : translations) {
			a.append('\t').append(t.getTranslation()).append('\n');
			for (Example e : t.getExamples()) {
				String s = e.getSentence();
				if (s != null) {
					a.append("\t\t").append(s).append('\n');
					s = e.getTranslation();
					if (s != null) a.append("\t\t\t").append(s).append('\n');
				}
			}
		}
	}

	long getOffset() {
		return offset;
	}

	void setOffset(long offset) {
		this.offset = offset;
	}

	long getCacheOffset() {
		return cacheOffset;
	}

	int getCacheLen() {
		return cacheLen;
	}

	void setCacheInfo(long cacheOffset, int cacheLen, byte dirProgress, byte revProgress) {
		checkProg(dirProgress);
		checkProg(revProgress);
		this.cacheOffset = cacheOffset;
		this.cacheLen = cacheLen;
		this.dirProgress = dirProgress;
		this.revProgress = revProgress;
	}

	private static final class Expr extends Word {
		private final String word;
		private final String expr;

		private Expr(String raw, long offset, String word, String expr) {
			super(raw, offset);
			this.word = word;
			this.expr = expr;
		}

		@Override
		public String getWord() {
			return word;
		}

		@Override
		public String getExpr() {
			return expr;
		}
	}
}
