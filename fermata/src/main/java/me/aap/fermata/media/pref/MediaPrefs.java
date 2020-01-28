package me.aap.fermata.media.pref;

import me.aap.fermata.function.BooleanSupplier;
import me.aap.fermata.function.DoubleSupplier;
import me.aap.fermata.function.IntSupplier;
import me.aap.fermata.function.Supplier;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.util.Utils;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;

/**
 * @author Andrey Pavlenko
 */
public interface MediaPrefs extends PreferenceStore {
	int MEDIA_ENG_MP = 0;
	int MEDIA_ENG_EXO = 1;
	Pref<IntSupplier> AUDIO_ENGINE = Pref.i("AUDIO_ENGINE", MEDIA_ENG_MP);
	Pref<IntSupplier> VIDEO_ENGINE = Pref.i("VIDEO_ENGINE", MEDIA_ENG_MP);
	Pref<DoubleSupplier> SPEED = Pref.f("SPEED", 1.0f).withInheritance(false);
	Pref<BooleanSupplier> AE_ENABLED = Pref.b("AE_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> EQ_ENABLED = Pref.b("EQ_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> VIRT_ENABLED = Pref.b("VIRT_ENABLED", false).withInheritance(false);
	Pref<BooleanSupplier> BASS_ENABLED = Pref.b("BASS_ENABLED", false).withInheritance(false);

	// 0 stands for Manual, negative for user presets, positive for system presets
	Pref<IntSupplier> EQ_PRESET = Pref.i("EQ_PRESET", 0).withInheritance(false);
	Pref<Supplier<int[]>> EQ_BANDS = Pref.ia("EQ_BANDS", () -> null).withInheritance(false);
	Pref<Supplier<String[]>> EQ_USER_PRESETS = Pref.sa("EQ_USER_PRESETS", new String[0]).withInheritance(false);
	Pref<IntSupplier> VIRT_MODE = Pref.i("VIRT_MODE", VIRTUALIZATION_MODE_AUTO).withInheritance(false);
	Pref<IntSupplier> VIRT_STRENGTH = Pref.i("VIRT_STRENGTH", 0).withInheritance(false);
	Pref<IntSupplier> BASS_STRENGTH = Pref.i("BASS_STRENGTH", 0).withInheritance(false);

	default int getAudioEnginePref() {
		return getIntPref(AUDIO_ENGINE);
	}

	default void setAudioEnginePref(int eng) {
		applyIntPref(AUDIO_ENGINE, eng);
	}

	default int getVideoEnginePref() {
		return getIntPref(VIDEO_ENGINE);
	}

	default void setVideoEnginePref(int eng) {
		applyIntPref(VIDEO_ENGINE, eng);
	}

	static String getUserPresetName(String preset) {
		return preset.substring(preset.indexOf(':') + 1);
	}

	static int[] getUserPresetBands(String preset) {
		String[] a = preset.substring(0, preset.indexOf(':')).split(" ");
		int[] bands = new int[a.length];

		for (int i = 0; i < bands.length; i++) {
			bands[i] = Integer.parseInt(a[i]);
		}

		return bands;
	}

	static String toUserPreset(String name, int[] bands) {
		StringBuilder sb = Utils.getSharedStringBuilder();

		for (int i = 0; i < bands.length; i++) {
			sb.append(bands[i]);
			if (i == (bands.length - 1)) sb.append(':');
			else sb.append(' ');
		}

		return sb.append(name).toString();
	}
}
