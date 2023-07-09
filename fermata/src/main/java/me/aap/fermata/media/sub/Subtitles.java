package me.aap.fermata.media.sub;

import static java.util.Collections.emptyList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.aap.utils.function.Consumer;

/**
 * @author Andrey Pavlenko
 */
public class Subtitles extends AbstractCollection<Subtitles.Text> {
	public static final Subtitles EMPTY = new Subtitles(emptyList());
	private final List<Text> subtitles;
	private int cursor;

	public Subtitles(Consumer<Builder> c) {
		this(new Builder(c));
	}

	private Subtitles(List<Text> subtitles) {
		this.subtitles = subtitles;
	}

	private Subtitles(Builder b) {
		var subtitles = b.subtitles;

		if (subtitles.isEmpty()) {
			this.subtitles = emptyList();
		} else {
			this.subtitles = subtitles;
			allign();
		}
	}

	private void allign() {
		Collections.sort(subtitles);
		for (int i = 0, n = subtitles.size() - 1; i < n; i++) {
			Text cur = subtitles.get(i);
			Text next = subtitles.get(i + 1);
			if (cur.getTime() + cur.getDuration() > next.getTime()) {
				int dur = (int) (next.getTime() - cur.getTime());
				if (dur == 0) {
					subtitles.remove(i);
					i--;
					n--;
				} else {
					cur.duration = dur;
				}
			}
		}
		for (int i = 0, n = subtitles.size(); i < n; i++) subtitles.get(i).index = i;

		if (subtitles instanceof ArrayList<Text> a) a.trimToSize();
	}

	public static final class Builder {
		private final List<Text> subtitles = new ArrayList<>();

		public Builder() {}

		private Builder(Consumer<Builder> c) {
			c.accept(this);
		}

		public Builder add(String text, long time, int duration) {
			subtitles.add(new Subtitles.Text(text, time, duration));
			return this;
		}

		public Subtitles build() {
			return new Subtitles(this);
		}
	}

	@NonNull
	@Override
	public Iterator<Text> iterator() {
		return subtitles.iterator();
	}

	@Override
	public int size() {
		return subtitles.size();
	}

	public Text get(int idx) {
		return subtitles.get(idx);
	}

	@Nullable
	public Text getNext(long time) {
		int idx = getNextIndex(time);
		return idx == -1 ? null : get(idx);
	}

	private int getNextIndex(long time) {
		var low = 0;
		var high = size() - 1;
		cursor = Math.min(cursor, high);

		while (low <= high) {
			var t = get(cursor);
			var cmp = t.compareTime(time);

			if (cmp == 0) {
				cursor++;
				return cursor - 1;
			} else if (cmp < 0) {
				if ((cursor == 0) || (get(cursor - 1).compareTime(time) > 0)) {
					cursor++;
					return cursor - 1;
				}
				high = cursor - 1;
			} else {
				low = cursor + 1;
			}

			cursor = (low + high) >>> 1;
		}

		return -1;
	}

	public void mergeWith(Subtitles sg) {
		subtitles.addAll(sg.subtitles);
		allign();
	}

	public boolean compareTime(Subtitles sg) {
		var s1 = subtitles;
		var s2 = sg.subtitles;
		if (s1.size() != s2.size()) return false;
		for (var i = s1.size() - 1; i >= 0; i--) {
			var t1 = s1.get(i);
			var t2 = s2.get(i);
			if ((t1.time != t2.time) || (t1.duration != t2.duration)) return false;
		}
		return true;
	}

	public static class Text implements Comparable<Text> {
		private final String text;
		private final long time;
		private int duration;
		private String translation;
		private int index;

		private Text(String text, long time, int duration) {
			this.text = text;
			this.time = time;
			this.duration = duration;
		}

		public String getText() {
			return text;
		}

		public long getTime() {
			return time;
		}

		public int getDuration() {
			return duration;
		}

		public String getTranslation() {
			return translation;
		}

		public void setTranslation(String translation) {
			this.translation = translation;
		}

		public int getIndex() {
			return index;
		}

		@Override
		public int compareTo(Text o) {
			return Long.compare(time, o.time);
		}

		public int compareTime(long time) {
			if (time == this.time) return 0;
			if (time < this.time) return -1;
			return time < this.time + duration ? 0 : 1;
		}
	}
}
