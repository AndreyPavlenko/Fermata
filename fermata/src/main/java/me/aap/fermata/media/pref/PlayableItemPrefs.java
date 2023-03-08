package me.aap.fermata.media.pref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface PlayableItemPrefs extends MediaPrefs {
	Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS", new String[0]).withInheritance(false);
	Pref<BooleanSupplier> WATCHED = Pref.b("WATCHED", false).withInheritance(false);
	Pref<LongSupplier> POSITION = Pref.l("POSITION", 0).withInheritance(false);
	Pref<LongSupplier> SUB_ID = Pref.l("SUB_ID", -1).withInheritance(false);
	Pref<LongSupplier> AUDIO_ID = Pref.l("AUDIO_ID", -1).withInheritance(false);

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
			e.removePref(POSITION);
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

	default Long getSubIdPref() {
		convertIntLongPref(SUB_ID);
		return hasPref(SUB_ID) ? getLongPref(SUB_ID) : null;
	}

	default void setSubIdPref(Long id) {
		convertIntLongPref(SUB_ID);
		if (id == null) removePref(SUB_ID);
		else applyLongPref(SUB_ID, id);
	}

	default Long getAudioIdPref() {
		convertIntLongPref(AUDIO_ID);
		return hasPref(AUDIO_ID) ? getLongPref(AUDIO_ID) : null;
	}

	default void setAudioIdPref(Long id) {
		convertIntLongPref(AUDIO_ID);
		if (id == null) removePref(AUDIO_ID);
		else applyLongPref(AUDIO_ID, id);
	}

	default void convertIntLongPref(Pref<LongSupplier> lpref) {
		try {
			getLongPref(lpref);
		} catch (ClassCastException ex) {
			Pref<IntSupplier> ipref = Pref.i(lpref.getName(), -1);
			long value = getIntPref(ipref);
			removePref(ipref);
			applyLongPref(lpref, value);
		}
	}

	static String bookmarkName(String bookmark) {
		return bookmark.substring(bookmark.indexOf(' ') + 1);
	}

	static int bookmarkTime(String bookmark) {
		return Integer.parseInt(bookmark.substring(0, bookmark.indexOf(' ')));
	}
}
