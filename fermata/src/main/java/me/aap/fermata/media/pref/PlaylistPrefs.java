package me.aap.fermata.media.pref;

import androidx.annotation.NonNull;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface PlaylistPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String>> PLAYLIST_NAME = Pref.s("NAME", "");
	Pref<Supplier<String[]>> PLAYLIST_ITEMS = Pref.sa("ITEMS", new String[0]);

	@NonNull
	PreferenceStore getPlaylistPreferenceStore();

	default String getPlaylistNamePref() {
		return getPlaylistPreferenceStore().getStringPref(PLAYLIST_NAME);
	}

	default void setPlaylistNamePref(String name) {
		getPlaylistPreferenceStore().applyStringPref(PLAYLIST_NAME, name);
	}

	default String[] getPlaylistItemsPref() {
		return getPlaylistPreferenceStore().getStringArrayPref(PLAYLIST_ITEMS);
	}

	default void setPlaylistItemsPref(String[] items) {
		getPlaylistPreferenceStore().applyStringArrayPref(PLAYLIST_ITEMS, items);
	}

	@Override
	default Pref<BooleanSupplier> getTitleNamePrefKey() {
		return PlaylistsPrefs.PLAYLIST_TITLE_NAME;
	}

	@Override
	default Pref<BooleanSupplier> getTitleFileNamePrefKey() {
		return PlaylistsPrefs.PLAYLIST_TITLE_FILE_NAME;
	}
}
