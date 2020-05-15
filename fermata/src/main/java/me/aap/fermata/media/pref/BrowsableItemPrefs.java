package me.aap.fermata.media.pref;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;

/**
 * @author Andrey Pavlenko
 */
public interface BrowsableItemPrefs extends MediaPrefs {
	Pref<BooleanSupplier> SHUFFLE = Pref.b("SHUFFLE", false);
	Pref<BooleanSupplier> REPEAT = Pref.b("REPEAT", false);
	Pref<Supplier<String>> REPEAT_ITEM = Pref.s("REPEAT_ITEM", (String) null).withInheritance(false);
	Pref<Supplier<String>> LAST_PLAYED_ITEM = Pref.s("LAST_PLAYED_ITEM", (String) null).withInheritance(false);
	Pref<LongSupplier> LAST_PLAYED_POS = Pref.l("LAST_PLAYED_POS", 0).withInheritance(false);
	Pref<BooleanSupplier> TITLE_SEQ_NUM = Pref.b("TITLE_SEQ_NUM", true);
	Pref<BooleanSupplier> TITLE_NAME = Pref.b("TITLE_NAME", true);
	Pref<BooleanSupplier> TITLE_FILE_NAME = Pref.b("TITLE_FILE_NAME", false);
	Pref<BooleanSupplier> SUBTITLE_NAME = Pref.b("SUBTITLE_NAME", false);
	Pref<BooleanSupplier> SUBTITLE_FILE_NAME = Pref.b("SUBTITLE_FILE_NAME", true);
	Pref<BooleanSupplier> SUBTITLE_ALBUM = Pref.b("SUBTITLE_ALBUM", false);
	Pref<BooleanSupplier> SUBTITLE_ARTIST = Pref.b("SUBTITLE_ARTIST", false);
	Pref<BooleanSupplier> SUBTITLE_DURATION = Pref.b("SUBTITLE_DURATION", true);
	Pref<BooleanSupplier> SHOW_TRACK_ICONS = Pref.b("SHOW_TRACK_ICONS", true);
	int SORT_BY_NONE = 0;
	int SORT_BY_NAME = 1;
	int SORT_BY_FILE_NAME = 2;
	int SORT_BY_DATE = 3;
	int SORT_BY_RND = 4;
	Pref<IntSupplier> SORT_BY = Pref.i("SORT_BY", SORT_BY_NONE);
	Pref<BooleanSupplier> SORT_DESC = Pref.b("SORT_DESC", false);

	default boolean getShufflePref() {
		return getBooleanPref(SHUFFLE);
	}

	default void setShufflePref(boolean shuffle) {
		applyBooleanPref(SHUFFLE, shuffle);
	}

	default boolean getRepeatPref() {
		return getBooleanPref(REPEAT);
	}

	default void setRepeatPref(boolean repeat) {
		applyBooleanPref(REPEAT, repeat);
	}

	default String getRepeatItemPref() {
		return getStringPref(REPEAT_ITEM);
	}

	default void setRepeatItemPref(String item) {
		applyStringPref(REPEAT_ITEM, item);
	}

	default String getLastPlayedItemPref() {
		return getStringPref(LAST_PLAYED_ITEM);
	}

	default void setLastPlayedItemPref(String item) {
		applyStringPref(LAST_PLAYED_ITEM, item);
	}

	default long getLastPlayedPosPref() {
		return getLongPref(LAST_PLAYED_POS);
	}

	default void setLastPlayedPosPref(long pos) {
		applyLongPref(LAST_PLAYED_POS, pos);
	}


	default boolean getTitleSeqNumPref() {
		return getBooleanPref(TITLE_SEQ_NUM);
	}

	default void setTitleSeqNumPref(boolean b) {
		applyBooleanPref(TITLE_SEQ_NUM, b);
	}

	default Pref<BooleanSupplier> getTitleNamePrefKey() {
		return TITLE_NAME;
	}

	default boolean getTitleNamePref() {
		return getBooleanPref(getTitleNamePrefKey());
	}

	default void setTitleNamePref(boolean b) {
		applyBooleanPref(getTitleNamePrefKey(), b);
	}

	default Pref<BooleanSupplier> getTitleFileNamePrefKey() {
		return TITLE_FILE_NAME;
	}

	default boolean getTitleFileNamePref() {
		return getBooleanPref(getTitleFileNamePrefKey());
	}

	default void setTitleFileNamePref(boolean b) {
		applyBooleanPref(getTitleFileNamePrefKey(), b);
	}

	default Pref<BooleanSupplier> getSubtitleNamePrefKey() {
		return SUBTITLE_NAME;
	}

	default boolean getSubtitleNamePref() {
		return getBooleanPref(getSubtitleNamePrefKey());
	}

	default void setSubtitleNamePref(boolean b) {
		applyBooleanPref(getSubtitleNamePrefKey(), b);
	}

	default Pref<BooleanSupplier> getSubtitleFileNamePrefKey() {
		return SUBTITLE_FILE_NAME;
	}

	default boolean getSubtitleFileNamePref() {
		return getBooleanPref(getSubtitleFileNamePrefKey());
	}

	default void setSubtitleFileNamePref(boolean b) {
		applyBooleanPref(getSubtitleFileNamePrefKey(), b);
	}

	default boolean getSubtitleAlbumPref() {
		return getBooleanPref(SUBTITLE_ALBUM);
	}

	default void setSubtitleAlbumPref(boolean b) {
		applyBooleanPref(SUBTITLE_ALBUM, b);
	}

	default boolean getSubtitleArtistPref() {
		return getBooleanPref(SUBTITLE_ARTIST);
	}

	default void setSubtitleArtistPref(boolean b) {
		applyBooleanPref(SUBTITLE_ARTIST, b);
	}

	default boolean getSubtitleDurationPref() {
		return getBooleanPref(SUBTITLE_DURATION);
	}

	default void setSubtitleDurationPref(boolean b) {
		applyBooleanPref(SUBTITLE_DURATION, b);
	}

	default boolean getShowTrackIconsPref() {
		return getBooleanPref(SHOW_TRACK_ICONS);
	}

	default void setShowTrackIconsPref(boolean b) {
		applyBooleanPref(SHOW_TRACK_ICONS, b);
	}

	default Pref<IntSupplier> getSortByPrefKey() {
		return SORT_BY;
	}

	default int getSortByPref() {
		return getIntPref(getSortByPrefKey());
	}

	default void setSortByPref(int sortBy) {
		applyIntPref(getSortByPrefKey(), sortBy);
	}

	default boolean getSortDescPref() {
		return getBooleanPref(SORT_DESC);
	}

	default void setSortDescPref(boolean desc) {
		applyBooleanPref(SORT_DESC, desc);
	}
}
