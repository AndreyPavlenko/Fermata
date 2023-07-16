package me.aap.fermata.media.pref;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import me.aap.fermata.action.Action;
import me.aap.fermata.action.Key;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.SharedPreferenceStore;

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
	Pref<IntSupplier> PREV_NEXT_LONG_TIME_UNIT =
			Pref.i("PREV_NEXT_LONG_TIME_UNIT", TIME_UNIT_PERCENT);
	Pref<BooleanSupplier> PLAY_PAUSE_STOP = Pref.b("PLAY_PAUSE_STOP", true);
	Pref<IntSupplier> VIDEO_CONTROL_START_DELAY = Pref.i("VIDEO_CONTROL_START_DELAY", 0);
	Pref<IntSupplier> VIDEO_CONTROL_TOUCH_DELAY = Pref.i("VIDEO_CONTROL_TOUCH_DELAY", 5);
	Pref<IntSupplier> VIDEO_CONTROL_SEEK_DELAY = Pref.i("VIDEO_CONTROL_SEEK_DELAY", 3);
	Pref<BooleanSupplier> VIDEO_AA_SHOW_STATUS = Pref.b("VIDEO_AA_SHOW_STATUS", false);

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

	default int getVideoControlStartDelayPref() {
		return getIntPref(VIDEO_CONTROL_START_DELAY);
	}

	default int getVideoControlTouchDelayPref() {
		return getIntPref(VIDEO_CONTROL_TOUCH_DELAY);
	}

	default int getVideoControlSeekDelayPref() {
		return getIntPref(VIDEO_CONTROL_SEEK_DELAY);
	}

	default boolean getVideoAaShowStatusPref() {
		return getBooleanPref(VIDEO_AA_SHOW_STATUS);
	}

	static long getTimeMillis(long dur, int time, int unit) {
		return switch (unit) {
			case PlaybackControlPrefs.TIME_UNIT_SECOND -> time * 1000L;
			case PlaybackControlPrefs.TIME_UNIT_MINUTE -> time * 60000L;
			default -> (long) (dur * ((float) time / 100));
		};
	}

	static PlaybackControlPrefs create(SharedPreferences prefs) {
		// Old prefs migration
		var prevVoiceCtrl = "PREV_VOICE_CONTROl";
		var nextVoiceCtrl = "NEXT_VOICE_CONTROl";

		if (prefs.contains(prevVoiceCtrl)) {
			if (prefs.getBoolean(prevVoiceCtrl, false)) {
				Key.getPrefs().applyIntPref(Key.MEDIA_PREVIOUS.getDblActionPref(),
						Action.ACTIVATE_VOICE_CTRL.ordinal());
			}
			prefs.edit().remove(prevVoiceCtrl).apply();
		}
		if (prefs.contains(nextVoiceCtrl)) {
			if (prefs.getBoolean(nextVoiceCtrl, false)) {
				Key.getPrefs()
						.applyIntPref(Key.MEDIA_NEXT.getDblActionPref(), Action.ACTIVATE_VOICE_CTRL.ordinal());
			}
			prefs.edit().remove(nextVoiceCtrl).apply();
		}


		class ControlPrefs extends BasicEventBroadcaster<Listener> implements PlaybackControlPrefs {

			@NonNull
			@Override
			public SharedPreferences getSharedPreferences() {
				return prefs;
			}
		}
		return new ControlPrefs();
	}
}
