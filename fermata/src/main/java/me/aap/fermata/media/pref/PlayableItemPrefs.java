package me.aap.fermata.media.pref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.aap.fermata.function.BooleanSupplier;
import me.aap.fermata.function.LongSupplier;
import me.aap.fermata.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface PlayableItemPrefs extends MediaPrefs {
	Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS", new String[0]).withInheritance(false);
	Pref<BooleanSupplier> WATCHED = Pref.b("WATCHED", false).withInheritance(false);
	Pref<LongSupplier> POSITION = Pref.l("POSITION", 0).withInheritance(false);

	default String[] getBookmarks() {
		return getStringArrayPref(BOOKMARKS);
	}

	default void setBookmarks(String[] bookmarks) {
		applyStringArrayPref(BOOKMARKS, bookmarks);
	}

	default boolean getWatchedPref() {
		return getBooleanPref(WATCHED);
	}

	default void setWatchedPref(boolean watched) {
		try (Edit e = editPreferenceStore()) {
			e.setBooleanPref(WATCHED, watched);
			if (watched) e.removePref(POSITION);
		}
	}

	default long getPositionPref() {
		return getLongPref(POSITION);
	}

	default void setPositionPref(long pos) {
		applyLongPref(POSITION, pos);
	}

	default void addBookmark(String name, int time) {
		List<String> bookmarks = new ArrayList<>(Arrays.asList(getBookmarks()));
		String b = time + " " + name;

		if (!bookmarks.contains(b)) {
			bookmarks.add(b);
			setBookmarks(bookmarks.toArray(new String[0]));
		}
	}

	default void removeBookmark(String name, int time) {
		List<String> bookmarks = new ArrayList<>(Arrays.asList(getBookmarks()));
		String b = time + " " + name;
		if (bookmarks.remove(b)) {
			if (bookmarks.isEmpty()) removePref(BOOKMARKS);
			else setBookmarks(bookmarks.toArray(new String[0]));
		}
	}

	static String bookmarkName(String bookmark) {
		return bookmark.substring(bookmark.indexOf(' ') + 1);
	}

	static int bookmarkTime(String bookmark) {
		return Integer.parseInt(bookmark.substring(0, bookmark.indexOf(' ')));
	}
}
