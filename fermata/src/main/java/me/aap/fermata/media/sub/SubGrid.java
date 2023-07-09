package me.aap.fermata.media.sub;

import static java.util.Collections.emptyMap;
import static me.aap.utils.collection.CollectionUtils.putIfAbsent;

import androidx.annotation.NonNull;

import java.util.AbstractCollection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import me.aap.utils.function.Consumer;

/**
 * @author Andrey Pavlenko
 */
public class SubGrid extends AbstractCollection<Map.Entry<SubGrid.Position, Subtitles>> {
	public static final SubGrid EMPTY = new SubGrid(emptyMap());
	private final Map<Position, Subtitles> subtitles;

	private SubGrid(Map<Position, Subtitles> subtitles) {
		this.subtitles = subtitles;
	}

	public SubGrid(Consumer<Builder> c) {
		this(new Builder(c));
	}

	private SubGrid(Builder b) {
		var m = new EnumMap<Position, Subtitles>(Position.class);
		for (Map.Entry<Position, Subtitles.Builder> e : b.subtitles.entrySet()) {
			var s = e.getValue().build();
			if (!s.isEmpty()) m.put(e.getKey(), s);
		}
		this.subtitles = m.isEmpty() ? emptyMap() : m;
	}

	public Subtitles get(Position position) {
		var s = subtitles.get(position);
		return s == null ? Subtitles.EMPTY : s;
	}

	public void remove(Position position) {
		subtitles.remove(position);
	}

	@Override
	public int size() {
		return subtitles.size();
	}

	@NonNull
	@Override
	public Iterator<Map.Entry<SubGrid.Position, Subtitles>> iterator() {
		return subtitles.entrySet().iterator();
	}

	public void mergeAtPosition(Position position) {
		if (size() == 1) {
			var e = iterator().next();
			var v = e.getValue();
			subtitles.remove(e.getKey());
			subtitles.put(position, v);
		} else {
			var sg = new SubGrid.Builder().add(this, position).build();
			subtitles.clear();
			subtitles.put(position, sg.get(position));
		}
	}

	public void mergeWith(SubGrid sg) {
		for (var e : sg) {
			var s = putIfAbsent(subtitles, e.getKey(), e.getValue());
			if (s != null) s.mergeWith(e.getValue());
		}
	}

	public enum Position {
		BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT, TOP_LEFT,
		TOP_CENTER, TOP_RIGHT
	}

	public static class Builder {
		private final Map<Position, Subtitles.Builder> subtitles;

		public Builder() {
			subtitles = new EnumMap<>(Position.class);
			for (Position p : Position.values()) {
				subtitles.put(p, new Subtitles.Builder());
			}
		}

		private Builder(Consumer<Builder> c) {
			this();
			c.accept(this);
		}

		public Builder add(String text, long time, int duration, Position position) {
			subtitles.get(position).add(text, time, duration);
			return this;
		}

		public Builder add(SubGrid grid) {
			for (var e : grid) add(e.getValue(), e.getKey());
			return this;
		}

		public Builder add(SubGrid grid, Position position) {
			for (var e : grid) add(e.getValue(), position);
			return this;
		}

		public Builder add(Subtitles subtitles, Position position) {
			var sb = this.subtitles.get(position);
			for (var t : subtitles)
				sb.add(t.getText(), t.getTime(), t.getDuration());
			return this;
		}

		public SubGrid build() {
			return new SubGrid(this);
		}
	}
}
