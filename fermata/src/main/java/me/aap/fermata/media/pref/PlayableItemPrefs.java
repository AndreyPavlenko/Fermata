package me.aap.fermata.media.pref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.aap.fermata.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface PlayableItemPrefs extends MediaPrefs {
	Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS", new String[0]);

	default String[] getBookmarks() {
		return getStringArrayPref(BOOKMARKS);
	}

	default void setBookmarks(String[] bookmarks) {
		applyStringArrayPref(BOOKMARKS, bookmarks);
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
