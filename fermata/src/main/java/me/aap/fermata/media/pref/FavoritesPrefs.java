package me.aap.fermata.media.pref;

import androidx.annotation.NonNull;

import java.util.function.Supplier;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface FavoritesPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> FAVORITES = Pref.sa("FAVORITES", new String[0]);

	@NonNull
	PreferenceStore getFavoritesPreferenceStore();

	default String[] getFavoritesPref() {
		return getFavoritesPreferenceStore().getStringArrayPref(FAVORITES);
	}

	default void setFavoritesPref(String[] favorites) {
		getFavoritesPreferenceStore().applyStringArrayPref(FAVORITES, favorites);
	}
}
