package me.aap.fermata.ui.fragment;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;
import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_DE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_EN;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_IT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_PT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_RU;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LOCALE_TR;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.action.Action;
import me.aap.fermata.action.Key;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.holder.BooleanHolder;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PrefUtils;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.PreferenceView;
import me.aap.utils.pref.PreferenceViewAdapter;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.FilePickerFragment;

/**
 * @author Andrey Pavlenko
 */
public class SettingsFragment extends MainActivityFragment
		implements MainActivityListener, PreferenceStore.Listener {
	private PreferenceViewAdapter adapter;

	@Override
	public int getFragmentId() {
		return R.id.settings_fragment;
	}

	@Override
	public CharSequence getTitle() {
		if (adapter != null) {
			var set = adapter.getPreferenceSet();
			if (set.getParent() != null) {
				var o = set.get();
				return (o.ctitle != null) ? o.ctitle : getResources().getString(o.title);
			}
		}
		return getResources().getString(R.string.settings);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(me.aap.utils.R.layout.pref_list_view, container, false);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (adapter != null) outState.putInt("id", adapter.getPreferenceSet().getId());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		super.onViewCreated(view, state);
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
			adapter = createAdapter(a);
			a.addBroadcastListener(this);
			a.getPrefs().addBroadcastListener(this);

			RecyclerView listView = view.findViewById(me.aap.utils.R.id.prefs_list_view);
			listView.setHasFixedSize(true);
			listView.setLayoutManager(new LinearLayoutManager(getContext()));
			listView.setAdapter(adapter);

			if (state != null) {
				PreferenceSet p = adapter.getPreferenceSet().find(state.getInt("id", ID_NULL));
				if (p != null) adapter.setPreferenceSet(p);
			}
		});
	}

	@Override
	public void onDestroyView() {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(this::cleanUp);
		super.onDestroyView();
	}

	private void cleanUp(MainActivityDelegate a) {
		a.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		FermataApplication.get().getAddonManager()
				.removeBroadcastListeners(l -> l instanceof AddonPrefsBuilder);
		if (adapter != null) adapter.onDestroy();
		adapter = null;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
			if (adapter == null) return;
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) adapter.setSize(a.getTextIconSize());
		});
	}

	@Override
	public boolean isRootPage() {
		return (adapter == null) || (adapter.getPreferenceSet().getParent() == null);
	}

	@Override
	public boolean onBackPressed() {
		if (adapter == null) return false;
		PreferenceSet p = adapter.getPreferenceSet().getParent();
		if (p == null) return false;
		adapter.setPreferenceSet(p);
		return true;
	}

	public static void addDelayPrefs(PreferenceSet set, PreferenceStore store,
																	 Pref<IntSupplier> pref,
																	 @StringRes int title, ChangeableCondition visibility) {
		set.addIntPref(o -> {
			o.store = store;
			o.pref = pref;
			o.title = title;
			o.seekMin = -5000;
			o.seekMax = 5000;
			o.seekScale = 50;
			o.ems = 3;
			o.visibility = visibility;
		});
	}

	public static void addAudioPrefs(PreferenceSet set, PreferenceStore store, boolean isCar) {
		addDelayPrefs(set, store, MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);

		if (!isCar) {
			set.addStringPref(o -> {
				Locale locale = Locale.getDefault();
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_LANG;
				o.title = R.string.preferred_audio_lang;
				o.stringHint = locale.getLanguage() + ' ' + locale.getISO3Language();
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_KEY;
				o.title = R.string.preferred_audio_key;
				o.stringHint = "studio1 studio2 default";
			});
		}
	}

	public static void addSubtitlePrefs(PreferenceSet set, PreferenceStore store, boolean isCar) {
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = MediaLibPrefs.SUB_ENABLED;
			o.title = R.string.display_subtitles;
		});

		addDelayPrefs(set, store, MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay,
				PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED));

		if (!isCar) {
			set.addStringPref(o -> {
				Locale locale = Locale.getDefault();
				o.store = store;
				o.pref = MediaLibPrefs.SUB_LANG;
				o.title = R.string.preferred_sub_lang;
				o.stringHint = locale.getLanguage() + ' ' + locale.getISO3Language();
				o.visibility = PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED);
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.SUB_KEY;
				o.title = R.string.preferred_sub_key;
				o.stringHint = "full forced";
				o.visibility = PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED);
			});
		}
	}

	private PreferenceViewAdapter createAdapter(MainActivityDelegate a) {
		MediaLibPrefs mediaPrefs = a.getMediaServiceBinder().getLib().getPrefs();
		int[] timeUnits =
				new int[]{R.string.time_unit_second, R.string.time_unit_minute,
						R.string.time_unit_percent};
		boolean isCar = a.isCarActivity();
		PreferenceSet set = new PreferenceSet();
		PreferenceSet sub1;
		PreferenceSet sub2;

		sub1 = set.subSet(o -> o.title = R.string.interface_prefs);

		if (BuildConfig.AUTO && a.isCarActivity()) {
			addAAInterface(a, sub1);
		} else {
			if (BuildConfig.AUTO) {
				addAAInterface(a, sub1.subSet(o -> o.title = R.string.interface_prefs_aa));
			}
			addInterface(a, sub1, MainActivityPrefs.THEME_MAIN, MainActivityPrefs.HIDE_BARS,
					MainActivityPrefs.FULLSCREEN, MainActivityPrefs.SHOW_PG_UP_DOWN, null,
					MainActivityPrefs.NAV_BAR_POS, MainActivityPrefs.NAV_BAR_SIZE,
					MainActivityPrefs.TOOL_BAR_SIZE, MainActivityPrefs.CONTROL_PANEL_SIZE,
					MainActivityPrefs.TEXT_ICON_SIZE);
		}

		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.SHOW_TRACK_ICONS;
			o.title = R.string.show_track_icons;
		});
		sub1.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.LOCALE;
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.values = new int[]{R.string.lang_en, R.string.lang_de, R.string.lang_it, R.string.lang_ru,
					R.string.lang_tr, R.string.lang_pt};
			o.valuesMap = new int[]{LOCALE_EN, LOCALE_DE, LOCALE_IT, LOCALE_RU, LOCALE_TR, LOCALE_PT};
			o.formatSubtitle = true;
			o.removeDefault = false;
		});

		sub1 = set.subSet(o -> o.title = R.string.key_bindings);
		var actions = Action.getAll();
		var actionNames = new int[actions.size()];
		var actionOrdinals = new int[actions.size()];
		for (var action : actions) {
			actionNames[action.ordinal()] = action.getName();
			actionOrdinals[action.ordinal()] = action.ordinal();
		}

		for (var k : Key.getAll()) {
			sub2 = sub1.subSet(o -> o.ctitle = k.name());
			sub2.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = k.getActionPref();
				o.title = R.string.key_on_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
			sub2.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = k.getLongActionPref();
				o.title = R.string.key_on_long_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
			sub2.addListPref(o -> {
				o.store = Key.getPrefs();
				o.pref = k.getDblActionPref();
				o.title = R.string.key_on_dbl_click;
				o.subtitle = R.string.string_format;
				o.values = actionNames;
				o.valuesMap = actionOrdinals;
				o.formatSubtitle = true;
			});
		}


		sub1 = set.subSet(o -> o.title = R.string.playback_settings);
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.PLAY_NEXT;
			o.title = R.string.play_next_on_completion;
		});

		sub1 = set.subSet(o -> o.title = R.string.playback_control);

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_long_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.prev_next_long_click);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub1.addBooleanPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PLAY_PAUSE_STOP;
			o.title = R.string.play_pause_stop;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.video_control);
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_START_DELAY;
			o.title = R.string.video_control_start_delay;
			o.seekMax = 60;
		});
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_TOUCH_DELAY;
			o.title = R.string.video_control_touch_delay;
			o.seekMax = 60;
		});
		sub2.addIntPref(o -> {
			o.store = a.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_SEEK_DELAY;
			o.title = R.string.video_control_seek_delay;
			o.seekMax = 60;
		});

		if (BuildConfig.AUTO) {
			sub2.addBooleanPref(o -> {
				o.store = a.getPlaybackControlPrefs();
				o.pref = PlaybackControlPrefs.VIDEO_AA_SHOW_STATUS;
				o.title = R.string.video_aa_show_status;
			});
		}

		if (!a.isCarActivity()) {
			sub1 = set.subSet(o -> o.title = R.string.voice_control);
			sub1.addBooleanPref(o -> {
				o.title = R.string.enable;
				o.pref = VOICE_CONTROl_ENABLED;
				o.store = a.getPrefs();
			});
			sub1.addBooleanPref(o -> {
				o.title = R.string.voice_control_fb;
				o.subtitle = R.string.voice_control_sub_long;
				o.pref = VOICE_CONTROl_FB;
				o.store = a.getPrefs();
				o.visibility = PrefCondition.create(a.getPrefs(), VOICE_CONTROl_ENABLED);
			});
			sub1.addStringPref(o -> {
				o.title = R.string.voice_control_subst;
				o.subtitle = R.string.voice_control_subst_sub;
				o.hint = R.string.voice_control_subst_hint;
				o.pref = VOICE_CONTROL_SUBST;
				o.store = a.getPrefs();
				o.maxLines = 10;
				o.visibility = PrefCondition.create(a.getPrefs(), VOICE_CONTROl_ENABLED);
			});
			sub1.addTtsLocalePref(o -> {
				o.title = R.string.lang;
				o.subtitle = me.aap.fermata.R.string.string_format;
				o.pref = VOICE_CONTROL_LANG;
				o.store = a.getPrefs();
				o.formatSubtitle = true;
				o.visibility = PrefCondition.create(a.getPrefs(), VOICE_CONTROl_ENABLED);
			});
		}

		PrefCondition<BooleanSupplier> exoCond =
				PrefCondition.create(mediaPrefs, MediaLibPrefs.EXO_ENABLED);
		PrefCondition<BooleanSupplier> vlcCond =
				PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		Consumer<PreferenceView.ListOpts> initList = o -> {
			if (o.visibility == null) o.visibility = exoCond.or(vlcCond);

			o.values =
					new int[]{R.string.engine_mp_name, R.string.engine_exo_name, R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_ENG_MP, MEDIA_ENG_EXO, MEDIA_ENG_VLC};
			o.valuesFilter = i -> {
				if (i == 1) return exoCond.get();
				if (i == 2) return vlcCond.get();
				return true;
			};
		};
		sub1 = set.subSet(o -> o.title = R.string.engine_prefs);
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.EXO_ENABLED;
			o.title = R.string.enable_exoplayer;
		});
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VLC_ENABLED;
			o.title = R.string.enable_vlcplayer;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.AUDIO_ENGINE;
			o.title = R.string.preferred_audio_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initList;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VIDEO_ENGINE;
			o.title = R.string.preferred_video_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initList;
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.MEDIA_SCANNER;
			o.title = R.string.preferred_media_scanner;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = vlcCond;
			o.values = new int[]{R.string.preferred_media_scanner_default,
					R.string.preferred_media_scanner_system, R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_SCANNER_DEFAULT, MEDIA_SCANNER_SYSTEM, MEDIA_SCANNER_VLC};
		});

		sub1 = set.subSet(o -> o.title = R.string.video_settings);
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaLibPrefs.VIDEO_SCALE;
			o.title = R.string.video_scaling;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.video_scaling_best, R.string.video_scaling_fill,
					R.string.video_scaling_orig, R.string.video_scaling_4, R.string.video_scaling_16};
		});
		sub1.addListPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaLibPrefs.HW_ACCEL;
			o.title = R.string.hw_accel;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.hw_accel_auto, R.string.hw_accel_full,
					R.string.hw_accel_decoding, R.string.hw_accel_disabled};
			o.visibility = vlcCond;
		});
		sub1.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.CLOCK_POS;
			o.title = R.string.clock_pos;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values =
					new int[]{R.string.clock_pos_none, R.string.clock_pos_left, R.string.clock_pos_right,
							R.string.clock_pos_center};
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.SYS_BARS_ON_VIDEO_TOUCH;
			o.title = R.string.sys_bars_on_video_touch;
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.LANDSCAPE_VIDEO;
			o.title = R.string.play_video_landscape;
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.CHANGE_BRIGHTNESS;
			o.title = R.string.change_brightness;
		});
		sub1.addIntPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.BRIGHTNESS;
			o.title = R.string.video_brightness;
			o.subtitle = R.string.change_brightness_sub;
			o.seekMin = 0;
			o.seekMax = 255;
			o.visibility = PrefCondition.create(a.getPrefs(), MainActivityPrefs.CHANGE_BRIGHTNESS);
		});

		sub2 = sub1.subSet(o -> {
			o.title = R.string.audio;
			o.visibility = vlcCond;
		});
		addAudioPrefs(sub2, mediaPrefs, isCar);

		sub1.addIntPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaPrefs.WATCHED_THRESHOLD;
			o.title = R.string.watched_threshold;
			o.subtitle = R.string.watched_threshold_sub;
			o.seekMin = 0;
			o.seekMax = 100;
			o.seekScale = 5;
		});

		sub1 = set.subSet(o -> o.title = R.string.subtitles);
		addSubtitlePrefs(sub1, mediaPrefs, isCar);

		addAddons(set);

		sub1 = set.subSet(o -> o.title = R.string.other);
		if (!a.isCarActivity() && Utils.isSafSupported(a)) {
			BooleanHolder reqPerm = new BooleanHolder(true);
			sub1.addButton(o -> {
				o.title = R.string.export_prefs;
				o.subtitle = R.string.export_prefs_sub;
				o.onClick = () -> exportPrefs(a);
			});
			sub1.addButton(o -> {
				o.title = R.string.import_prefs;
				o.subtitle = R.string.import_prefs_sub;
				o.onClick = () -> {
					if (reqPerm.value) {
						reqPerm.value = false;
						if (FilePickerFragment.requestManageAllFilesPerm(a.getContext())) return;
					}
					importPrefs(a);
				};
			});
		}
		if (BuildConfig.AUTO) {
			sub1.addBooleanPref(o -> {
				o.store = a.getPrefs();
				o.pref = MainActivityPrefs.CHECK_UPDATES;
				o.title = R.string.check_updates;
			});
		}

		return new PreferenceViewAdapter(set) {
			@Override
			public void setPreferenceSet(PreferenceSet set) {
				super.setPreferenceSet(set);
				a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			}
		};
	}

	private void addAAInterface(MainActivityDelegate a, PreferenceSet ps) {
		if (BuildConfig.AUTO) {
			addInterface(a, ps, MainActivityPrefs.THEME_AA, MainActivityPrefs.HIDE_BARS_AA,
					MainActivityPrefs.FULLSCREEN_AA, MainActivityPrefs.SHOW_PG_UP_DOWN_AA,
					MainActivityPrefs.USE_DPAD_CURSOR, MainActivityPrefs.NAV_BAR_POS_AA,
					MainActivityPrefs.NAV_BAR_SIZE_AA, MainActivityPrefs.TOOL_BAR_SIZE_AA,
					MainActivityPrefs.CONTROL_PANEL_SIZE_AA, MainActivityPrefs.TEXT_ICON_SIZE_AA);
		}
	}

	private void addInterface(MainActivityDelegate a, PreferenceSet ps, Pref<IntSupplier> theme,
														Pref<BooleanSupplier> hideBars, Pref<BooleanSupplier> fullScreen,
														Pref<BooleanSupplier> pgUpDown, Pref<BooleanSupplier> dpadCursor,
														Pref<IntSupplier> nbPos, Pref<DoubleSupplier> nbSize,
														Pref<DoubleSupplier> tbSize, Pref<DoubleSupplier> cpSize,
														Pref<DoubleSupplier> textIconSize) {
		ps.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = theme;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_dark, R.string.theme_light, R.string.theme_day_night,
					R.string.theme_black, R.string.theme_star_wars};
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = hideBars;
			o.title = R.string.hide_bars;
			o.subtitle = R.string.hide_bars_sub;
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = fullScreen;
			o.title = R.string.fullscreen_mode;
		});
		ps.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = pgUpDown;
			o.title = R.string.show_pg_up_down;
		});
		if (dpadCursor != null) {
			ps.addBooleanPref(o -> {
				o.store = a.getPrefs();
				o.pref = dpadCursor;
				o.title = R.string.use_dpad_cursor;
			});
		}
		ps.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = nbPos;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_bottom, R.string.nav_bar_pos_left,
					R.string.nav_bar_pos_right};
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = nbSize;
			o.title = R.string.nav_bar_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = tbSize;
			o.title = R.string.tool_bar_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = cpSize;
			o.title = R.string.control_panel_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
		ps.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = textIconSize;
			o.title = R.string.text_icon_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
	}

	private void addAddons(PreferenceSet set) {
		AddonManager amgr = FermataApplication.get().getAddonManager();
		PreferenceSet sub = set.subSet(o -> o.title = R.string.addons);
		PreferenceStore store = FermataApplication.get().getPreferenceStore();

		for (AddonInfo addon : BuildConfig.ADDONS) {
			AddonPrefsBuilder b = new AddonPrefsBuilder(amgr, addon, store);
			PreferenceSet sub1 = sub.subSet(b);
			sub1.configure(b::configure);
		}
	}

	private void exportPrefs(MainActivityDelegate a) {
		a.startActivityForResult(() -> new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
				.onCompletion((d, err) -> {
					Context ctx = a.getContext();

					if (err != null) {
						UiUtils.showAlert(ctx, ctx.getString(R.string.export_prefs_failed, err));
						return;
					}

					if (d == null) return;
					Uri uri = d.getData();
					if (uri == null) return;

					try {
						DocumentFile dir = DocumentFile.fromTreeUri(ctx, uri);
						if (dir == null) return;

						File prefsDir = PrefUtils.getSharedPrefsFile(ctx, "fermata").getParentFile();
						File[] files = (prefsDir == null) ? null :
								prefsDir.listFiles(n -> !n.getName().equals("image-cache.xml"));

						if ((files == null) || (files.length == 0)) {
							UiUtils.showAlert(ctx, R.string.prefs_not_found);
							return;
						}

						SimpleDateFormat fmt = new SimpleDateFormat("ddMMyy", Locale.getDefault());
						String pattern = "Fermata_prefs_" + fmt.format(new Date());
						String name = pattern + ".zip";
						for (int i = 1; dir.findFile(name) != null; i++) name = pattern + '_' + i + ".zip";
						DocumentFile f = dir.createFile("application/zip", name);

						if (f == null) {
							UiUtils.showAlert(ctx,
									ctx.getString(R.string.export_prefs_failed, "Failed to create file"));
							return;
						}

						List<String> names = new ArrayList<>(files.length);

						for (File pf : files) {
							String n = pf.getName();
							if (n.endsWith(".xml")) names.add(n.substring(0, n.length() - 4));
						}

						try (OutputStream os = ctx.getContentResolver().openOutputStream(f.getUri());
								 ZipOutputStream zos = (SDK_INT >= N) ? new ZipOutputStream(os, UTF_8) :
										 new ZipOutputStream(os)) {
							PrefUtils.exportSharedPrefs(ctx, names, zos);
						}

						UiUtils.showInfo(ctx, ctx.getString(R.string.export_prefs_ok, f.getName()));
					} catch (Exception ex) {
						Log.e(ex, "Failed to export preferences");
						UiUtils.showAlert(ctx, ctx.getString(R.string.export_prefs_failed, ex));
					}
				});
	}

	private void importPrefs(MainActivityDelegate a) {
		a.startActivityForResult(
						() -> new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip"))
				.onCompletion((d, err) -> {
					Context ctx = a.getContext();

					if (err != null) {
						UiUtils.showAlert(ctx, ctx.getString(R.string.import_prefs_failed, err));
						return;
					}

					if (d == null) return;
					Uri uri = d.getData();
					if (uri == null) return;

					try {
						DocumentFile f = DocumentFile.fromSingleUri(ctx, uri);
						if (f == null) return;

						try (InputStream is = ctx.getContentResolver().openInputStream(f.getUri());
								 ZipInputStream zis = (SDK_INT >= N) ? new ZipInputStream(is, UTF_8) :
										 new ZipInputStream(is)) {
							PrefUtils.importSharedPrefs(ctx, zis);
						}

						UiUtils.showInfo(ctx, ctx.getString(R.string.import_prefs_ok)).thenRun(() -> {
							App.get().getHandler().postDelayed(() -> System.exit(0), 1000);
							if (ctx instanceof Activity) ((Activity) ctx).finishAffinity();
							else System.exit(0);
						});
					} catch (Exception ex) {
						Log.e(ex, "Failed to export preferences");
						UiUtils.showAlert(ctx, ctx.getString(R.string.import_prefs_failed, ex));
					}
				});
	}

	private static final class AddonPrefsBuilder
			implements Consumer<PreferenceView.Opts>, AddonManager.Listener {
		private final AddonManager amgr;
		private final AddonInfo info;
		private final PreferenceStore store;
		private PreferenceSet set;

		public AddonPrefsBuilder(AddonManager amgr, AddonInfo info, PreferenceStore store) {
			this.amgr = amgr;
			this.info = info;
			this.store = store;
			amgr.addBroadcastListener(this);
		}

		void configure(PreferenceSet set) {
			this.set = set;

			set.addBooleanPref(o -> {
				o.title = R.string.enable;
				o.pref = info.enabledPref;
				o.store = store;
			});

			FermataAddon a = amgr.getAddon(info.className);

			if (a != null) {
				ChangeableCondition cond = PrefCondition.create(store, info.enabledPref);
				a.contributeSettings(store, set, cond);
			}
		}

		@Override
		public void accept(PreferenceView.Opts o) {
			o.title = info.addonName;
			o.icon = info.icon;
		}

		@Override
		public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
			if (set != null) set.configure(this::configure);
		}
	}
}
