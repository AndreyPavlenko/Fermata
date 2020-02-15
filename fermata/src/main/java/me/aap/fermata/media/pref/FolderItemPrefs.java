package me.aap.fermata.media.pref;

import me.aap.utils.function.IntSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface FolderItemPrefs extends BrowsableItemPrefs {
	Pref<IntSupplier> FOLDER_SORT_BY = SORT_BY.withDefaultValue(() -> SORT_BY_FILE_NAME);

	@Override
	default Pref<IntSupplier> getSortByPrefKey() {
		return FOLDER_SORT_BY;
	}
}
