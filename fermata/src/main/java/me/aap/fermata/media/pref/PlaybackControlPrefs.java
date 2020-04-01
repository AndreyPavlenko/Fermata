package me.aap.fermata.media.pref;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;

import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.event.BasicEventBroadcaster;

/**
 * @author Andrey Pavlenko
 */
public interface PlaybackControlPrefs extends SharedPreferenceStore {
	int TIME_UNIT_SECOND = 0;
	int TIME_UNIT_MINUTE = 1;
	int TIME_UNIT_PERCENT = 2;
	Pref<IntSupplier> RW_FF_TIME = Pref.i("RW_FF_TIME", 5);
	Pref<IntSupplier> RW_FF_TIME_UNIT = Pref.i("RW_FF_TIME_UNIT", TIME_UNIT_SECOND);
	Pref<IntSupplier> RW_FF_LONG_TIME = Pref.i("RW_FF_LONG_TIME", 20);
	Pref<IntSupplier> RW_FF_LONG_TIME_UNIT = Pref.i("RW_FF_LONG_TIME_UNIT", TIME_UNIT_SECOND);
	Pref<IntSupplier> PREV_NEXT_LONG_TIME = Pref.i("PREV_NEXT_LONG_TIME", 5);
	Pref<IntSupplier> PREV_NEXT_LONG_TIME_UNIT = Pref.i("PREV_NEXT_LONG_TIME_UNIT", TIME_UNIT_PERCENT);
	Pref<BooleanSupplier> PLAY_PAUSE_STOP = Pref.b("PLAY_PAUSE_STOP", true);

	default int getRwFfTimePref() {
		return getIntPref(RW_FF_TIME);
	}

	default int getRwFfTimeUnitPref() {
		return getIntPref(RW_FF_TIME_UNIT);
	}

	default int getRwFfLongTimePref() {
		return getIntPref(RW_FF_LONG_TIME);
	}

	default int getRwFfLongTimeUnitPref() {
		return getIntPref(RW_FF_LONG_TIME_UNIT);
	}

	default int getPrevNextLongTimePref() {
		return getIntPref(PREV_NEXT_LONG_TIME);
	}

	default int getPrevNextLongTimeUnitPref() {
		return getIntPref(PREV_NEXT_LONG_TIME_UNIT);
	}

	default boolean getPlayPauseStopPref() {
		return getBooleanPref(PLAY_PAUSE_STOP);
	}

	static long getTimeMillis(long dur, int time, int unit) {
		switch (unit) {
			case PlaybackControlPrefs.TIME_UNIT_SECOND:
				return time * 1000;
			case PlaybackControlPrefs.TIME_UNIT_MINUTE:
				return time * 60000;
			default:
				return (long) (dur * ((float) time / 100));
		}
	}

	static PlaybackControlPrefs create(SharedPreferences prefs) {
		class ControlPrefs extends BasicEventBroadcaster<Listener>
				implements PlaybackControlPrefs {

			@NonNull
			@Override
			public SharedPreferences getSharedPreferences() {
				return prefs;
			}
		}
		return new ControlPrefs();
	}
}
