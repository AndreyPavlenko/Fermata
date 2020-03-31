package me.aap.fermata.media.pref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface PlayableItemPrefs extends MediaPrefs {
	Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS", new String[0]).withInheritance(false);
	Pref<BooleanSupplier> WATCHED = Pref.b("WATCHED", false).withInheritance(false);
	Pref<LongSupplier> POSITION = Pref.l("POSITION", 0).withInheritance(false);
	Pref<IntSupplier> SUB_ID = Pref.i("SUB_ID", -1).withInheritance(false);
	Pref<IntSupplier> AUDIO_ID = Pref.i("AUDIO_ID", -1).withInheritance(false);

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

	default Integer getSubIdPref() {
		return hasPref(SUB_ID) ? getIntPref(SUB_ID) : null;
	}

	default void setSubIdPref(Integer id) {
		if (id == null) removePref(SUB_ID);
		else applyIntPref(SUB_ID, id);
	}

	default Integer getAudioIdPref() {
		return hasPref(AUDIO_ID) ? getIntPref(AUDIO_ID) : null;
	}

	default void setAudioIdPref(Integer id) {
		if (id == null) removePref(AUDIO_ID);
		else applyIntPref(AUDIO_ID, id);
	}

	static String bookmarkName(String bookmark) {
		return bookmark.substring(bookmark.indexOf(' ') + 1);
	}

	static int bookmarkTime(String bookmark) {
		return Integer.parseInt(bookmark.substring(0, bookmark.indexOf(' ')));
	}
}
