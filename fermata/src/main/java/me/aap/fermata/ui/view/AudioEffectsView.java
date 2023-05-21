package me.aap.fermata.ui.view;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.pref.MediaPrefs.AE_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_PRESET;
import static me.aap.utils.function.CheckedRunnable.runWithRetry;

import android.content.Context;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.Arrays;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.ShortConsumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.PreferenceView;
import me.aap.utils.pref.PreferenceView.BooleanOpts;
import me.aap.utils.pref.PreferenceView.ListOpts;
import me.aap.utils.ui.UiUtils;

/**
 * @author Andrey Pavlenko
 */
public class AudioEffectsView extends ScrollView implements PreferenceStore.Listener {
	private final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
	private final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);
	@Nullable
	private PreferenceStore store;
	@Nullable
	private PreferenceStore ctrlPrefs;
	@Nullable
	private AudioEffects effects;

	public AudioEffectsView(Context context) {
		this(context, null);
		setLayoutParams(new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
	}

	public AudioEffectsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setBackgroundColor(Color.TRANSPARENT);
	}

	@Nullable
	public AudioEffects getEffects() {
		return effects;
	}

	public void init(MediaSessionCallback cb, AudioEffects effects, PlayableItem pi) {
		this.effects = effects;
		this.store = new BasicPreferenceStore();
		this.store.addBroadcastListener(this);
		inflate(getContext(), R.layout.audio_effects, this);

		Equalizer eq = effects.getEqualizer();
		Virtualizer virt = effects.getVirtualizer();
		BassBoost bass = effects.getBassBoost();
		LoudnessEnhancer le = effects.getLoudnessEnhancer();

		// Equalizer
		if (eq != null) {
			ctrlPrefs = cb.getPlaybackControlPrefs();
			short numPresets = eq.getNumberOfPresets();
			String[] userPresets = ctrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
			String[] presetNames = new String[numPresets + userPresets.length + 1];
			int preset = getEqPreset(pi, ctrlPrefs);
			if (preset < 0) preset = -preset + numPresets;

			configureSwitch(findViewById(R.id.equalizer_switch), () -> eq);
			findViewById(R.id.equalizer_preset_save).setOnClickListener(this::savePreset);
			findViewById(R.id.equalizer_preset_delete).setOnClickListener(this::deletePreset);
			createBands(eq);

			presetNames[0] = getResources().getString(R.string.eq_manual);

			for (short i = 0; i < numPresets; i++) {
				presetNames[i + 1] = presetName(eq.getPresetName(i));
			}

			for (int i = 0; i < userPresets.length; i++) {
				presetNames[i + numPresets + 1] = MediaPrefs.getUserPresetName(userPresets[i]);
			}

			PreferenceView pref = findViewById(R.id.equalizer_preset);
			pref.setPreference(null, () -> {
				ListOpts o = new ListOpts();
				o.store = store;
				o.pref = MediaPrefs.EQ_PRESET;
				o.title = R.string.string_format;
				o.formatTitle = true;
				o.stringValues = presetNames;
				return o;
			});

			store.applyIntPref(MediaPrefs.EQ_PRESET, preset);
		} else {
			hide(R.id.equalizer, R.id.equalizer_title, R.id.equalizer_switch);
		}

		// Virtualizer
		if (virt != null) {
			configureSwitch(findViewById(R.id.virtualizer_switch), () -> virt);
			configureSeek(findViewById(R.id.virtualizer_seek), virt::getRoundedStrength, virt::setStrength);

			PreferenceView pref = findViewById(R.id.virtualizer_mode);
			pref.setPreference(null, () -> {
				ListOpts o = new ListOpts();
				o.store = store;
				o.pref = MediaPrefs.VIRT_MODE;
				o.title = R.string.string_format;
				o.formatTitle = true;
				o.values = new int[]{R.string.auto, R.string.binaural, R.string.transaural};
				o.valuesMap = new int[]{VIRTUALIZATION_MODE_AUTO, VIRTUALIZATION_MODE_BINAURAL,
						VIRTUALIZATION_MODE_TRANSAURAL};
				return o;
			});
		} else {
			hide(R.id.virtualizer, R.id.virtualizer_title, R.id.virtualizer_switch);
		}

		// BassBoost
		if (bass != null) {
			configureSwitch(findViewById(R.id.bass_switch), () -> bass);
			configureSeek(findViewById(R.id.bass_seek), bass::getRoundedStrength, bass::setStrength);
		} else {
			hide(R.id.bass, R.id.bass_title, R.id.bass_switch);
		}

		// LoudnessEnhancer
		if (le != null) {
			configureSwitch(findViewById(R.id.vol_boost_switch), () -> le);
			configureSeek(findViewById(R.id.vol_boost_seek), () -> (int) (le.getTargetGain() / 10),
					g -> le.setTargetGain(g * 10));
		} else {
			hide(R.id.vol_boost, R.id.vol_boost_title, R.id.vol_boost_switch);
		}

		// Apply to
		if (pi.getPrefs().getBooleanPref(AE_ENABLED)) {
			store.applyBooleanPref(TRACK, true);
		} else if (pi.getParent().getPrefs().getBooleanPref(AE_ENABLED)) {
			store.applyBooleanPref(FOLDER, true);
		}

		PreferenceView pref = findViewById(R.id.track);
		pref.setPreference(null, () -> {
			BooleanOpts o = new BooleanOpts();
			o.store = store;
			o.pref = TRACK;
			o.title = R.string.current_track;
			return o;
		});

		pref = findViewById(R.id.folder);
		pref.setPreference(null, () -> {
			BooleanOpts o = new BooleanOpts();
			o.store = store;
			o.pref = FOLDER;
			o.title = R.string.current_folder;
			return o;
		});
	}

	public void cleanup() {
		removeAllViews();
		store = null;
		ctrlPrefs = null;
		effects = null;
	}

	public void apply(MediaSessionCallback cb) {
		if (store == null) return;

		MediaEngine eng = cb.getEngine();

		if (eng != null) {
			PlayableItem pi = eng.getSource();

			if (pi != null) {
				if (store.getBooleanPref(TRACK)) {
					apply(pi.getPrefs());
					return;
				} else if (store.getBooleanPref(FOLDER)) {
					apply(pi.getParent().getPrefs());
					clearPrefs(pi.getPrefs());
					return;
				} else {
					clearPrefs(pi.getPrefs());
					clearPrefs(pi.getParent().getPrefs());
				}
			}
		}

		apply(cb.getPlaybackControlPrefs());
	}

	private void apply(PreferenceStore ps) {
		runWithRetry(() -> applyPrefs(ps));
	}

	private void applyPrefs(PreferenceStore ps) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			PreferenceStore store = requireNonNull(this.store);
			AudioEffects effects = requireNonNull(this.effects);
			Equalizer eq = requireNonNull(effects.getEqualizer());
			Virtualizer virt = requireNonNull(effects.getVirtualizer());
			BassBoost bass = requireNonNull(effects.getBassBoost());
			LoudnessEnhancer le = requireNonNull(effects.getLoudnessEnhancer());
			boolean enabled = false;

			if (eq.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.EQ_ENABLED, true);
				int preset = store.getIntPref(MediaPrefs.EQ_PRESET);
				short numPresets = eq.getNumberOfPresets();

				if (preset == 0) {
					int[] bands = getBandValues(eq);
					e.setIntPref(MediaPrefs.EQ_PRESET, 0);
					e.setIntArrayPref(MediaPrefs.EQ_BANDS, bands);
				} else if (preset <= numPresets) {
					e.setIntPref(MediaPrefs.EQ_PRESET, (short) preset);
				} else {
					e.setIntPref(MediaPrefs.EQ_PRESET, (short) (numPresets - preset));
				}
			} else {
				e.removePref(MediaPrefs.EQ_ENABLED);
				e.removePref(MediaPrefs.EQ_PRESET);
				e.removePref(MediaPrefs.EQ_BANDS);
			}

			if (virt.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.VIRT_ENABLED, true);
				e.setIntPref(MediaPrefs.VIRT_MODE, store.getIntPref(MediaPrefs.VIRT_MODE));
				e.setIntPref(MediaPrefs.VIRT_STRENGTH, virt.getRoundedStrength());
			} else {
				e.removePref(MediaPrefs.VIRT_ENABLED);
				e.removePref(MediaPrefs.VIRT_MODE);
				e.removePref(MediaPrefs.VIRT_STRENGTH);
			}

			if (bass.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.BASS_ENABLED, true);
				e.setIntPref(MediaPrefs.BASS_STRENGTH, bass.getRoundedStrength());
			} else {
				e.removePref(MediaPrefs.BASS_ENABLED);
				e.removePref(MediaPrefs.BASS_STRENGTH);
			}

			if (le.getEnabled()) {
				enabled = true;
				e.setBooleanPref(MediaPrefs.VOL_BOOST_ENABLED, true);
				e.setIntPref(MediaPrefs.VOL_BOOST_STRENGTH, (int) (le.getTargetGain() / 10));
			} else {
				e.removePref(MediaPrefs.VOL_BOOST_ENABLED);
				e.removePref(MediaPrefs.VOL_BOOST_STRENGTH);
			}

			if (enabled) e.setBooleanPref(AE_ENABLED, true);
			else e.removePref(AE_ENABLED);
		}
	}

	private void clearPrefs(PreferenceStore ps) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.removePref(MediaPrefs.AE_ENABLED);
			e.removePref(MediaPrefs.EQ_ENABLED);
			e.removePref(MediaPrefs.EQ_PRESET);
			e.removePref(MediaPrefs.EQ_BANDS);
			e.removePref(MediaPrefs.VIRT_ENABLED);
			e.removePref(MediaPrefs.VIRT_MODE);
			e.removePref(MediaPrefs.VIRT_STRENGTH);
			e.removePref(MediaPrefs.BASS_ENABLED);
			e.removePref(MediaPrefs.BASS_STRENGTH);
			e.removePref(MediaPrefs.VOL_BOOST_ENABLED);
			e.removePref(MediaPrefs.VOL_BOOST_STRENGTH);
		}
	}

	private void createBands(Equalizer eq) {
		short[] range = eq.getBandLevelRange();
		int sbMax = range[1] - range[0];
		String minText = String.valueOf(range[0] / 100);
		String maxText = String.valueOf(range[1] / 100);
		ViewGroup bands = findViewById(R.id.equalizer_bands);
		LayoutInflater inflater = LayoutInflater.from(getContext());

		for (short n = eq.getNumberOfBands(), i = 0; i < n; i++) {
			inflater.inflate(R.layout.equalizer_band, bands);
			ViewGroup bandView = (ViewGroup) bands.getChildAt(i);
			TextView label = bandView.findViewById(R.id.eq_band_title);
			AppCompatSeekBar sb = bandView.findViewById(R.id.eq_band_seek);
			TextView min = bandView.findViewById(R.id.eq_band_min);
			TextView max = bandView.findViewById(R.id.eq_band_max);
			float freq = (float) eq.getCenterFreq(i) / 1000;
			short band = i;
			sb.setMax(sbMax);
			sb.setProgress(eq.getBandLevel(i) - range[0]);
			min.setText(minText);
			max.setText(maxText);
			sb.setOnSeekBarChangeListener(new SeekBarListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					runWithRetry(() -> eqBandChanged(eq, band, progress, fromUser));
				}
			});

			if (freq >= 1000) {
				freq /= 1000;
				String strFreq = String.format((freq == Math.floor(freq)) ? "%.0f" : "%.1f", freq);
				label.setText(getResources().getString(R.string.eq_khz, strFreq));
			} else {
				label.setText(getResources().getString(R.string.eq_hz, String.valueOf((int) freq)));
			}
		}
	}

	private void setBandValues(Equalizer eq) {
		short[] range = eq.getBandLevelRange();
		ViewGroup bandsView = findViewById(R.id.equalizer_bands);

		for (short n = eq.getNumberOfBands(), i = 0; i < n; i++) {
			ViewGroup band = (ViewGroup) bandsView.getChildAt(i);
			AppCompatSeekBar sb = band.findViewById(R.id.eq_band_seek);
			sb.setProgress(eq.getBandLevel(i) - range[0]);
		}
	}

	private void setUserBandValues(Equalizer eq, String[] presets, int preset) {
		short[] range = eq.getBandLevelRange();
		int[] bands = MediaPrefs.getUserPresetBands(presets[preset]);
		ViewGroup bandsView = findViewById(R.id.equalizer_bands);

		for (short n = eq.getNumberOfBands(), i = 0; (i < n) && (i < bands.length); i++) {
			ViewGroup band = (ViewGroup) bandsView.getChildAt(i);
			AppCompatSeekBar sb = band.findViewById(R.id.eq_band_seek);
			eq.setBandLevel(i, (short) bands[i]);
			sb.setProgress(eq.getBandLevel(i) - range[0]);
		}
	}

	private void configureSwitch(CompoundButton sw, Supplier<AudioEffect> effect) {
		sw.setChecked(effect.get().getEnabled());
		sw.setOnCheckedChangeListener((b, checked) -> runWithRetry(() -> effect.get().setEnabled(checked)));
	}

	private void configureSeek(SeekBar sb, IntSupplier get, ShortConsumer set) {
		sb.setMax(1000);
		sb.setProgress(get.getAsInt());
		sb.setOnSeekBarChangeListener(new SeekBarListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				runWithRetry(() -> set.accept((short) progress));
			}
		});
	}

	private void eqBandChanged(Equalizer eq, short band, int progress, boolean fromUser) {
		if (!fromUser) return;

		PreferenceStore store = requireNonNull(this.store);
		short[] range = eq.getBandLevelRange();
		eq.setBandLevel(band, (short) (progress + range[0]));

		int p = store.getIntPref(MediaPrefs.EQ_PRESET);
		if ((p != 0) && (p <= eq.getNumberOfPresets())) store.applyIntPref(EQ_PRESET, 0);
	}

	private void savePreset(View v) {
		PreferenceView pref = findViewById(R.id.equalizer_preset);
		ListOpts opts = (ListOpts) pref.getOpts();
		int p = opts.store.getIntPref(MediaPrefs.EQ_PRESET);
		Equalizer eq = requireNonNull(requireNonNull(effects).getEqualizer());
		short numPresets = eq.getNumberOfPresets();
		PreferenceStore ctrlPrefs = requireNonNull(this.ctrlPrefs);

		if (p > numPresets) {
			int[] bands = getBandValues(eq);
			String[] userPresets = ctrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
			userPresets[p - numPresets - 1] = MediaPrefs.toUserPreset(opts.stringValues[p], bands);
			ctrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
		} else {
			UiUtils.queryText(getContext(), R.string.preset_name, R.drawable.equalizer).onSuccess(name -> {
				if (name == null) return;

				int[] bands = getBandValues(eq);
				String[] userPresets = ctrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
				userPresets = Arrays.copyOf(userPresets, userPresets.length + 1);
				userPresets[userPresets.length - 1] = MediaPrefs.toUserPreset(name, bands);
				opts.stringValues = Arrays.copyOf(opts.stringValues, opts.stringValues.length + 1);
				opts.stringValues[opts.stringValues.length - 1] = name;
				ctrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
				opts.store.applyIntPref(MediaPrefs.EQ_PRESET, opts.stringValues.length - 1);
			});
		}
	}

	private void deletePreset(View v) {
		PreferenceView pref = findViewById(R.id.equalizer_preset);
		ListOpts opts = (ListOpts) pref.getOpts();
		int p = opts.store.getIntPref(MediaPrefs.EQ_PRESET);
		Equalizer eq = requireNonNull(requireNonNull(effects).getEqualizer());
		short numPresets = eq.getNumberOfPresets();
		PreferenceStore ctrlPrefs = requireNonNull(this.ctrlPrefs);
		String[] userPresets = ctrlPrefs.getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
		userPresets = CollectionUtils.remove(userPresets, (p - numPresets - 1));
		opts.stringValues = CollectionUtils.remove(opts.stringValues, p);
		ctrlPrefs.applyStringArrayPref(MediaPrefs.EQ_USER_PRESETS, userPresets);
		opts.store.applyIntPref(MediaPrefs.EQ_PRESET, 0);
	}

	private int[] getBandValues(Equalizer eq) {
		short n = eq.getNumberOfBands();
		int[] bands = new int[n];
		for (short i = 0; i < n; i++) {
			bands[i] = eq.getBandLevel(i);
		}
		return bands;
	}

	private void hide(@IdRes int... ids) {
		for (int id : ids) {
			findViewById(id).setVisibility(GONE);
		}
	}

	private void show(@IdRes int id) {
		findViewById(id).setVisibility(VISIBLE);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		runWithRetry(() -> preferenceChanged(store, prefs));
	}

	private void preferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (prefs.contains(MediaPrefs.EQ_PRESET)) {
			int p = store.getIntPref(MediaPrefs.EQ_PRESET);

			if (p == 0) {
				show(R.id.equalizer_preset_save);
				hide(R.id.equalizer_preset_delete);
				return;
			}

			AudioEffects effects = requireNonNull(this.effects);
			Equalizer eq = requireNonNull(effects.getEqualizer());
			short numPresets = eq.getNumberOfPresets();

			if (p > numPresets) {
				p -= numPresets + 1;
				String[] presets = requireNonNull(ctrlPrefs).getStringArrayPref(MediaPrefs.EQ_USER_PRESETS);
				show(R.id.equalizer_preset_save);
				show(R.id.equalizer_preset_delete);
				if (p < presets.length) setUserBandValues(eq, presets, p);
				return;
			}

			hide(R.id.equalizer_preset_save, R.id.equalizer_preset_delete);
			eq.usePreset((short) (p - 1));
			setBandValues(eq);
		} else if (prefs.contains(MediaPrefs.VIRT_MODE)) {
			AudioEffects effects = requireNonNull(this.effects);
			Virtualizer virt = requireNonNull(effects.getVirtualizer());
			virt.forceVirtualizationMode(store.getIntPref(MediaPrefs.VIRT_MODE));
		} else if (prefs.contains(TRACK) && store.getBooleanPref(TRACK)) {
			store.applyBooleanPref(FOLDER, false);
		} else if (prefs.contains(FOLDER) && store.getBooleanPref(FOLDER)) {
			store.applyBooleanPref(TRACK, false);
		}
	}

	@Override
	public View focusSearch(View focused, int direction) {
		if ((direction == FOCUS_UP) && (focused != null) && (focused.getId() == R.id.equalizer_switch)) {
			View v = MainActivityDelegate.get(getContext()).getToolBar()
					.findViewById(me.aap.utils.R.id.tool_bar_back_button);
			if (v.getVisibility() == VISIBLE) return v;
		}

		return super.focusSearch(focused, direction);
	}

	private String presetName(String name) {
		switch (name) {
			case "Manual":
				return getResources().getString(R.string.eq_manual);
			case "Normal":
				return getResources().getString(R.string.eq_normal);
			case "Classical":
				return getResources().getString(R.string.eq_classical);
			case "Dance":
				return getResources().getString(R.string.eq_dance);
			case "Flat":
				return getResources().getString(R.string.eq_flat);
			case "Folk":
				return getResources().getString(R.string.eq_folk);
			case "Heavy Metal":
				return getResources().getString(R.string.eq_heavy_metal);
			case "Hip Hop":
				return getResources().getString(R.string.eq_hip_hop);
			case "Jazz":
				return getResources().getString(R.string.eq_jazz);
			case "Pop":
				return getResources().getString(R.string.eq_pop);
			case "Rock":
				return getResources().getString(R.string.eq_rock);
			default:
				return name;
		}
	}

	private static int getEqPreset(PlayableItem pi, PreferenceStore ctrlPrefs) {
		MediaPrefs prefs = pi.getPrefs();

		if (prefs.getBooleanPref(AE_ENABLED)) {
			return prefs.getIntPref(EQ_PRESET);
		} else {
			prefs = pi.getParent().getPrefs();
			return prefs.getBooleanPref(AE_ENABLED) ? prefs.getIntPref(EQ_PRESET) :
					ctrlPrefs.getIntPref(EQ_PRESET);
		}
	}

	private static abstract class SeekBarListener implements SeekBar.OnSeekBarChangeListener {
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	}
}
