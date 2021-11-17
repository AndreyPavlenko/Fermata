package me.aap.fermata.media.pref;

import androidx.annotation.NonNull;

import me.aap.utils.function.Supplier;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface FoldersPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> FOLDERS = Pref.sa("FOLDERS", new String[0]);

	@NonNull
	PreferenceStore getFoldersPreferenceStore();

	default String[] getFoldersPref() {
		return getFoldersPreferenceStore().getStringArrayPref(FOLDERS);
	}

	default void setFoldersPref(String[] folders) {
		getFoldersPreferenceStore().applyStringArrayPref(FOLDERS, folders);
	}
}
