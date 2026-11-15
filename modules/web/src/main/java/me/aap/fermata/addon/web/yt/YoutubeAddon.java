package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.BuildConfig.AUTO;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeAddon extends WebBrowserAddon implements PreferenceStore.Listener {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(YoutubeAddon.class.getName());
	public static final int YT_DARK_MODE_DISABLED = 0;
	public static final int YT_DARK_MODE_ENABLED = 1;
	public static final int YT_DARK_MODE_AUTO = 2;
	private static final Pref<IntSupplier> YT_DARK_MODE = Pref.i("YT_DARK_MODE", YT_DARK_MODE_AUTO);
	private static final Pref<BooleanSupplier> YT_DESKTOP_VERSION = Pref.b("YT_DESKTOP_VERSION", false);
	private static final Pref<Supplier<String[]>> YT_BOOKMARKS = Pref.sa("YT_BOOKMARKS");
	private static final Pref<Supplier<String>> VIDEO_SCALE = Pref.s("VIDEO_SCALE", VideoScale.CONTAIN::prefName);
	private static final Pref<BooleanSupplier> YT_OPEN_ON_START = Pref.b("YT_OPEN_ON_START", false);
	private static final Pref<BooleanSupplier> YT_AUTO_HIGHEST_QUALITY =
			Pref.b("YT_AUTO_HIGHEST_QUALITY", false);
	private static final Pref<BooleanSupplier> YT_SKIP_ADD = AUTO ? Pref.b("YT_SKIP_ADD", true) : null;
	private static final Pref<BooleanSupplier> YT_RESUME_LAST = Pref.b("YT_RESUME_LAST", true);
	private static final Pref<Supplier<String>> YT_LAST_VIDEO_URL = Pref.s("YT_LAST_VIDEO_URL", (String) null);
	private static final Pref<IntSupplier> YT_LAST_VIDEO_POS = Pref.i("YT_LAST_VIDEO_POS", 0);
	// When it was last saved, i.e. last seen playing or paused, as System.currentTimeMillis()
	private static final Pref<LongSupplier> YT_LAST_VIDEO_TIME = Pref.l("YT_LAST_VIDEO_TIME", 0L);
	// Minutes since the last video was left within which a car screen resumes it, 0 for no limit
	private static final Pref<IntSupplier> YT_RESUME_LAST_WITHIN =
			AUTO ? Pref.i("YT_RESUME_LAST_WITHIN", 60) : null;
	private static final Pref<BooleanSupplier> YT_PLAYER_CONTROLS =
			Pref.b("YT_PLAYER_CONTROLS", false);
	private static final Pref<BooleanSupplier> YT_PAUSE_ON_LEAVE = Pref.b("YT_PAUSE_ON_LEAVE", !AUTO);
	private boolean ignorePrefChange;

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@NonNull
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new YoutubeFragment();
	}

	@Override
	public Pref<IntSupplier> getForceDarkPref() {
		return YT_DARK_MODE;
	}

	@Override
	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return YT_DESKTOP_VERSION;
	}

	@Override
	public Pref<Supplier<String[]>> getBookmarksPref() {
		return YT_BOOKMARKS;
	}

	boolean skipAd() {
		return AUTO && getPreferenceStore().getBooleanPref(YT_SKIP_ADD);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		super.contributeSettings(ctx, store, set, visibility);
		getPreferenceStore().addBroadcastListener(this);
		MainActivityPrefs.get().addBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().addBroadcastListener(this);

		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_OPEN_ON_START;
			o.title = R.string.open_on_start;
			o.visibility = visibility;
		});
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_RESUME_LAST;
			o.title = R.string.resume_last_video;
			o.subtitle = R.string.resume_last_video_sub;
			o.visibility = visibility;
		});
		if (AUTO) {
			set.addListPref(o -> {
				o.store = getPreferenceStore();
				o.pref = YT_RESUME_LAST_WITHIN;
				o.title = R.string.resume_last_video_within;
				o.subtitle = me.aap.fermata.R.string.string_format;
				o.formatSubtitle = true;
				o.values = new int[]{R.string.minutes_10, R.string.minutes_30, R.string.hours_1,
						R.string.hours_2, R.string.hours_6, R.string.hours_12, R.string.no_limit};
				o.valuesMap = new int[]{10, 30, 60, 120, 360, 720, 0};
				o.visibility = visibility.and(PrefCondition.create(getPreferenceStore(), YT_RESUME_LAST));
			});
		}
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_AUTO_HIGHEST_QUALITY;
			o.title = R.string.auto_highest_video_quality;
			o.visibility = visibility;
		});
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_PLAYER_CONTROLS;
			o.title = R.string.youtube_player_controls;
			o.subtitle = R.string.youtube_player_controls_sub;
			o.visibility = visibility;
		});
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = YT_PAUSE_ON_LEAVE;
			o.title = R.string.youtube_pause_on_leave;
			o.subtitle = R.string.youtube_pause_on_leave_sub;
			o.visibility = visibility;
		});

		if (AUTO) {
			set.addBooleanPref(o -> {
				o.store = getPreferenceStore();
				o.pref = YT_SKIP_ADD;
				o.title = R.string.try_to_skip_ad;
				o.visibility = visibility;
			});
		}

		YoutubeSponsorBlock.contributeSettings(getPreferenceStore(), set, visibility);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (ignorePrefChange) return;
		ignorePrefChange = true;

		if (prefs.contains(getInfo().enabledPref)) {
			if (!store.getBooleanPref(getInfo().enabledPref)) {
				MainActivityPrefs ap = MainActivityPrefs.get();
				getPreferenceStore().applyBooleanPref(YT_OPEN_ON_START, false);
				if (getInfo().className.equals(ap.getShowAddonOnStartPref()))
					ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(YT_OPEN_ON_START)) {
			MainActivityPrefs ap = MainActivityPrefs.get();
			if (store.getBooleanPref(YT_OPEN_ON_START)) {
				ap.setShowAddonOnStartPref(getInfo().className);
			} else if (getInfo().className.equals(ap.getShowAddonOnStartPref())) {
				ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(MainActivityPrefs.SHOW_ADDON_ON_START)) {
			getPreferenceStore().applyBooleanPref(YT_OPEN_ON_START,
					getInfo().className.equals(MainActivityPrefs.get().getShowAddonOnStartPref()));
		}

		ignorePrefChange = false;
	}

	@Override
	public void uninstall() {
		getPreferenceStore().removeBroadcastListener(this);
		MainActivityPrefs.get().removeBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().removeBroadcastListener(this);
	}

	VideoScale getScale() {
		switch (getPreferenceStore().getStringPref(VIDEO_SCALE)) {
			case "fill":
				return VideoScale.FILL;
			case "contain":
				return VideoScale.CONTAIN;
			case "cover":
				return VideoScale.COVER;
			default:
				return VideoScale.NONE;
		}
	}

	void setScale(VideoScale scale) {
		getPreferenceStore().applyStringPref(VIDEO_SCALE, scale.prefName());
	}

	boolean pauseOnLeave() {
		return getPreferenceStore().getBooleanPref(YT_PAUSE_ON_LEAVE);
	}

	boolean autoHighestQuality() {
		return getPreferenceStore().getBooleanPref(YT_AUTO_HIGHEST_QUALITY);
	}

	boolean youtubePlayerControls() {
		return getPreferenceStore().getBooleanPref(YT_PLAYER_CONTROLS);
	}

	boolean autoHighestQualityChanged(List<Pref<?>> prefs) {
		return prefs.contains(YT_AUTO_HIGHEST_QUALITY);
	}

	/**
	 * The last watched video at its saved position, or null if resuming is disabled or there is nothing to
	 * resume. In the car, also if it was left longer ago than the set limit: it may well have been
	 * finished elsewhere since. Unlike the activity state, this survives the process being killed when
	 * Android Auto disconnects.
	 */
	@Nullable
	YoutubeWebView.Video getLastVideo(boolean car) {
		PreferenceStore ps = getPreferenceStore();
		if (!ps.getBooleanPref(YT_RESUME_LAST)) return null;
		String url = ps.getStringPref(YT_LAST_VIDEO_URL);
		if (!YoutubeUrls.isVideoUrl(url)) return null;
		long time = ps.getLongPref(YT_LAST_VIDEO_TIME);
		long left = SystemClock.elapsedRealtime() - Math.max(0L, System.currentTimeMillis() - time);
		if (car && !isRecentInCar(left)) return null;
		return new YoutubeWebView.Video(url, ps.getIntPref(YT_LAST_VIDEO_POS) * 1000L, true, left);
	}

	// Whether a video left at this time, as SystemClock.elapsedRealtime(), may be resumed in the car
	boolean isRecentInCar(long left) {
		PreferenceStore ps = getPreferenceStore();
		if (!AUTO || !ps.getBooleanPref(YT_RESUME_LAST)) return false;
		long limit = ps.getIntPref(YT_RESUME_LAST_WITHIN) * 60000L;
		return (limit == 0L) || (SystemClock.elapsedRealtime() - left <= limit);
	}

	void saveLastVideo(@Nullable String url, int posSec) {
		if (!getPreferenceStore().getBooleanPref(YT_RESUME_LAST) || !YoutubeUrls.isVideoUrl(url)) return;
		try (PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			e.setStringPref(YT_LAST_VIDEO_URL, url);
			e.setIntPref(YT_LAST_VIDEO_POS, posSec);
			e.setLongPref(YT_LAST_VIDEO_TIME, System.currentTimeMillis());
		}
	}

	// Only the saved video: another may have ended, e.g. a muted preview
	void clearLastVideo(@Nullable String endedUrl) {
		String id = YoutubeUrls.videoId(endedUrl);
		if (id.isEmpty() || !id.equals(YoutubeUrls.videoId(getPreferenceStore().getStringPref(YT_LAST_VIDEO_URL)))) return;
		try (PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			e.removePref(YT_LAST_VIDEO_URL);
			e.removePref(YT_LAST_VIDEO_POS);
			e.removePref(YT_LAST_VIDEO_TIME);
		}
	}

	enum VideoScale {
		FILL, CONTAIN, COVER, NONE;

		String prefName() {
			return name().toLowerCase();
		}
	}
}
