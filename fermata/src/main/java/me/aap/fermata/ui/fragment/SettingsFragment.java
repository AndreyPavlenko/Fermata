package me.aap.fermata.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceView;
import me.aap.utils.pref.PreferenceViewAdapter;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;
import static me.aap.fermata.ui.activity.MainActivityListener.FRAGMENT_CONTENT_CHANGED;
import static me.aap.utils.ui.UiUtils.ID_NULL;

/**
 * @author Andrey Pavlenko
 */
public class SettingsFragment extends MainActivityFragment {
	private PreferenceViewAdapter adapter;

	@Override
	public int getFragmentId() {
		return R.id.settings_fragment;
	}

	@Override
	public CharSequence getTitle() {
		PreferenceSet set = adapter.getPreferenceSet();
		if (set.getParent() != null) return getResources().getString(set.get().title);
		else return getResources().getString(R.string.settings);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.pref_list_view, container, false);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (adapter != null) outState.putInt("id", adapter.getPreferenceSet().getId());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		super.onViewCreated(view, state);
		if (adapter == null) adapter = createAdapter();

		RecyclerView listView = view.findViewById(R.id.prefs_list_view);
		listView.setHasFixedSize(true);
		listView.setLayoutManager(new LinearLayoutManager(getContext()));
		listView.setAdapter(adapter);

		if (state != null) {
			PreferenceSet p = adapter.getPreferenceSet().find(state.getInt("id", ID_NULL));
			if (p != null) adapter.setPreferenceSet(p);
		}
	}

	@Override
	public boolean isRootPage() {
		return adapter.getPreferenceSet().getParent() == null;
	}

	@Override
	public boolean onBackPressed() {
		PreferenceSet p = adapter.getPreferenceSet().getParent();
		if (p == null) return false;
		adapter.setPreferenceSet(p);
		return true;
	}

	public static void addDelayPrefs(PreferenceSet set, PreferenceStore store,
																	 PreferenceStore.Pref<IntSupplier> pref, @StringRes int title,
																	 ChangeableCondition visibility) {
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

	@NonNull
	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private PreferenceViewAdapter createAdapter() {
		MainActivityDelegate a = getMainActivity();
		MediaLibPrefs mediaPrefs = a.getMediaServiceBinder().getLib().getPrefs();
		int[] timeUnits = new int[]{R.string.time_unit_second, R.string.time_unit_minute,
				R.string.time_unit_percent};
		boolean isCar = a.isCarActivity();
		PreferenceSet set = new PreferenceSet();
		PreferenceSet sub1;
		PreferenceSet sub2;

		sub1 = set.subSet(o -> o.title = R.string.interface_prefs);
		sub1.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.THEME;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_dark, R.string.theme_light, R.string.theme_day_night, R.string.theme_black};
		});
		sub1.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.NAV_BAR_POS;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_bottom, R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
		});
		if (BuildConfig.AUTO) {
			sub1.addListPref(o -> {
				o.store = a.getPrefs();
				o.pref = MainActivityPrefs.NAV_BAR_POS_AA;
				o.title = R.string.nav_bar_pos_aa;
				o.subtitle = R.string.nav_bar_pos_sub;
				o.formatSubtitle = true;
				o.values = new int[]{R.string.nav_bar_pos_bottom, R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
			});
		}
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.HIDE_BARS;
			o.title = R.string.hide_bars;
			o.subtitle = R.string.hide_bars_sub;
		});
		sub1.addBooleanPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.FULLSCREEN;
			o.title = R.string.fullscreen_mode;
		});
		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.SHOW_TRACK_ICONS;
			o.title = R.string.show_track_icons;
		});
		sub1.addFloatPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.MEDIA_ITEM_SCALE;
			o.title = R.string.text_icon_scale;
			o.scale = 0.1f;
			o.seekMin = 1;
			o.seekMax = 20;
		});

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

		PrefCondition<BooleanSupplier> exoCond = PrefCondition.create(mediaPrefs, MediaLibPrefs.EXO_ENABLED);
		PrefCondition<BooleanSupplier> vlcCond = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		Consumer<PreferenceView.ListOpts> initList = o -> {
			if (o.visibility == null) o.visibility = exoCond.or(vlcCond);

			o.values = new int[]{R.string.engine_mp_name, R.string.engine_exo_name, R.string.engine_vlc_name};
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
			o.values = new int[]{R.string.preferred_media_scanner_default, R.string.preferred_media_scanner_system, R.string.engine_vlc_name};
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

		sub2 = sub1.subSet(o -> {
			o.title = R.string.audio;
			o.visibility = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		});
		addAudioPrefs(sub2, mediaPrefs, isCar);

		sub2 = sub1.subSet(o -> {
			o.title = R.string.subtitles;
			o.visibility = PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		});
		addSubtitlePrefs(sub2, mediaPrefs, isCar);

		addAddons(set);

		return new PreferenceViewAdapter(set) {
			@Override
			public void setPreferenceSet(PreferenceSet set) {
				super.setPreferenceSet(set);
				a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			}
		};
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

	private static final class AddonPrefsBuilder implements Consumer<PreferenceView.Opts>, AddonManager.Listener {
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
