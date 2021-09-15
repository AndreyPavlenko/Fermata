package me.aap.fermata.media.pref;

/**
 * @author Andrey Pavlenko
 */
public interface StreamItemPrefs extends PlayableItemPrefs, BrowsableItemPrefs {

	@Override
	default int getSortByPref() {
		return SORT_BY_NONE;
	}
}
