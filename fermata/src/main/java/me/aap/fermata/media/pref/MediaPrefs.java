package me.aap.fermata.media.pref;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.TextUtils;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;

/**
 * @author Andrey Pavlenko
 */
public interface MediaPrefs extends PreferenceStore {
	int MEDIA_ENG_MP = 0;
	int MEDIA_ENG_EXO = 1;
	int MEDIA_ENG_VLC = 2;
	int SCALE_BEST = 0;
	int SCALE_FILL = 1;
	int SCALE_ORIGINAL = 2;
	int SCALE_4_3 = 3;
	int SCALE_16_9 = 4;
	Pref<IntSupplier> AUDIO_ENGINE = Pref.i("AUDIO_ENGINE", MEDIA_ENG_MP);
	Pref<IntSupplier> VIDEO_ENGINE = Pref.i("VIDEO_ENGINE", MEDIA_ENG_MP);
	Pref<IntSupplier> VIDEO_SCALE = Pref.i("VIDEO_SCALE", SCALE_BEST);
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

	Pref<BooleanSupplier> SUB_ENABLED = Pref.b("SUB_ENABLED", true);
	Pref<IntSupplier> SUB_DELAY = Pref.i("SUB_DELAY", 0);
	Pref<Supplier<String>> SUB_LANG = Pref.s("SUB_LANG", "");
	Pref<Supplier<String>> SUB_KEY = Pref.s("SUB_KEY", "");
	Pref<IntSupplier> AUDIO_DELAY = Pref.i("AUDIO_DELAY", 0);
	Pref<Supplier<String>> AUDIO_LANG = Pref.s("AUDIO_LANG", "");
	Pref<Supplier<String>> AUDIO_KEY = Pref.s("AUDIO_KEY", "");

	default int getAudioEnginePref() {
		return getIntPref(AUDIO_ENGINE);
	}

	default void setAudioEnginePref(int eng) {
		try (Edit e = editPreferenceStore(false)) {
			e.setIntPref(AUDIO_ENGINE, eng);
		}
	}

	default int getVideoEnginePref() {
		return getIntPref(VIDEO_ENGINE);
	}

	default void setVideoEnginePref(int eng) {
		try (Edit e = editPreferenceStore(false)) {
			e.setIntPref(VIDEO_ENGINE, eng);
		}
	}

	default int getVideoScalePref() {
		return getIntPref(VIDEO_SCALE);
	}

	default void setVideoScalePref(int scale) {
		try (Edit e = editPreferenceStore(false)) {
			e.setIntPref(VIDEO_SCALE, scale);
		}
	}

	default boolean getSubEnabledPref() {
		return getBooleanPref(SUB_ENABLED);
	}

	default int getSubDelayPref() {
		return getIntPref(SUB_DELAY);
	}

	default String getSubLangPref() {
		return getStringPref(SUB_LANG);
	}

	default String getSubKeyPref() {
		return getStringPref(SUB_KEY);
	}

	default int getAudioDelayPref() {
		return getIntPref(AUDIO_DELAY);
	}

	default String getAudioLangPref() {
		return getStringPref(AUDIO_LANG);
	}

	default String getAudioKeyPref() {
		return getStringPref(AUDIO_KEY);
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
		StringBuilder sb = TextUtils.getSharedStringBuilder();

		for (int i = 0; i < bands.length; i++) {
			sb.append(bands[i]);
			if (i == (bands.length - 1)) sb.append(':');
			else sb.append(' ');
		}

		return sb.append(name).toString();
	}
}
