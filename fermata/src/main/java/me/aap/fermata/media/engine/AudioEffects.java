package me.aap.fermata.media.engine;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.Nullable;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class AudioEffects {
	private static final byte EQUALIZER = 1;
	private static final byte VIRTUALIZER = 2;
	private static final byte BASS_BOOST = 4;
	private static final byte LOUDNESS_ENHANCER = 8;
	private static final byte supported;
	private final Equalizer equalizer;
	private final Virtualizer virtualizer;
	private final BassBoost bassBoost;
	private final LoudnessEnhancer loudnessEnhancer;

	static {
		byte s = 0;
		for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
			if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) s |= EQUALIZER;
			else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) s |= VIRTUALIZER;
			else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) s |= BASS_BOOST;
			else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) s |= LOUDNESS_ENHANCER;
		}
		supported = s;
	}

	private AudioEffects(int priority, int audioSessionId) {
		equalizer = supported(EQUALIZER) ? new Equalizer(priority, audioSessionId) : null;
		virtualizer = supported(VIRTUALIZER) ? new Virtualizer(priority, audioSessionId) : null;
		bassBoost = supported(BASS_BOOST) ? new BassBoost(priority, audioSessionId) : null;
		loudnessEnhancer = supported(LOUDNESS_ENHANCER) ? new LoudnessEnhancer(audioSessionId) : null;
	}

	private static boolean supported(byte type) {
		return (supported & type) != 0;
	}

	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0) return null;

		try {
			return new AudioEffects(priority, audioSessionId);
		} catch (Exception ex) {
			// Sometimes it fails with RuntimeException: AudioEffect: set/get parameter error
			Log.w("Failed to create AudioEffects - retrying...");

			try {
				Thread.sleep(300);
				return new AudioEffects(priority, audioSessionId);
			} catch (Exception ex1) {
				Log.e(ex1, "Failed to create AudioEffects");
				return null;
			}
		}
	}

	@Nullable
	public Equalizer getEqualizer() {
		return equalizer;
	}

	@Nullable
	public Virtualizer getVirtualizer() {
		return virtualizer;
	}

	@Nullable
	public BassBoost getBassBoost() {
		return bassBoost;
	}

	@Nullable
	public LoudnessEnhancer getLoudnessEnhancer() {
		return loudnessEnhancer;
	}

	public void release() {
		if (equalizer != null) equalizer.release();
		if (virtualizer != null) virtualizer.release();
		if (bassBoost != null) bassBoost.release();
		if (loudnessEnhancer != null) loudnessEnhancer.release();
	}
}
