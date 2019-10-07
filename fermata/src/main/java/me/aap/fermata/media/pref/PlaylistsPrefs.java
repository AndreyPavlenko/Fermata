package me.aap.fermata.media.pref;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface PlaylistsPrefs extends BrowsableItemPrefs {
	Pref<IntSupplier> PLAYLIST_COUNTER = Pref.i("PLAYLIST_COUNTER", 0);
	Pref<Supplier<int[]>> PLAYLIST_IDS = Pref.ia("PLAYLIST_IDS", () -> new int[0]);
	Pref<BooleanSupplier> PLAYLIST_TITLE_NAME = TITLE_NAME.withDefaultValue(() -> true);
	Pref<BooleanSupplier> PLAYLIST_TITLE_FILE_NAME = TITLE_FILE_NAME.withDefaultValue(() -> false);

	default int getPlaylistsCounterPref() {
		return getRootPreferenceStore().getIntPref(PLAYLIST_COUNTER);
	}

	default void setPlaylistsCounterPref(int counter) {
		getRootPreferenceStore().applyIntPref(PLAYLIST_COUNTER, counter);
	}

	default int[] getPlaylistIdsPref() {
		return getRootPreferenceStore().getIntArrayPref(PLAYLIST_IDS);
	}

	default void setPlaylistIdsPref(int[] ids) {
		getRootPreferenceStore().applyIntArrayPref(PLAYLIST_IDS, ids);
	}

	@Override
	default Pref<BooleanSupplier> getTitleNamePrefKey() {
		return PLAYLIST_TITLE_NAME;
	}

	@Override
	default Pref<BooleanSupplier> getTitleFileNamePrefKey() {
		return PLAYLIST_TITLE_FILE_NAME;
	}
}
