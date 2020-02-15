package me.aap.fermata.media.pref;

import androidx.annotation.NonNull;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public interface FoldersPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> FOLDERS = Pref.sa("FOLDERS", new String[0]);
	Pref<BooleanSupplier> PREFER_FILE_API = Pref.b("PREFER_FILE_API", true);

	@NonNull
	PreferenceStore getFoldersPreferenceStore();

	default String[] getFoldersPref() {
		return getFoldersPreferenceStore().getStringArrayPref(FOLDERS);
	}

	default void setFoldersPref(String[] folders) {
		getFoldersPreferenceStore().applyStringArrayPref(FOLDERS, folders);
	}

	default boolean getPreferFileApiPref() {
		return getFoldersPreferenceStore().getBooleanPref(PREFER_FILE_API);
	}

	default void setPreferFileApiPref(boolean prefer) {
		getFoldersPreferenceStore().applyBooleanPref(PREFER_FILE_API, prefer);
	}
}
