package me.aap.fermata.ui.activity;

import static me.aap.fermata.media.pref.PlaybackControlPrefs.TIME_UNIT_SECOND;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.res.Resources;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.log.Log;
import me.aap.utils.text.PatternCompat;

/**
 * @author Andrey Pavlenko
 */
class VoiceSearchHandler {
	private static final String ACTION = "A";
	private static final String LOCATION = "L";
	private static final String QUERY = "Q";
	private static final String NUMBER = "N";
	private static final String UNIT = "U";
	private final MainActivityDelegate activity;
	private final Pattern aFind;
	private final Pattern aOpen;
	private final Pattern aPause;
	private final Pattern aStop;
	private final Pattern aPlay;
	private final Pattern aPlayFavorites;
	private final Pattern lFolders;
	private final Pattern lFavorites;
	private final Pattern lPlaylists;
	private final Pattern lTV;
	private final Pattern lYoutube;
	private final Pattern lBrowser;
	private final Pattern uSecond;
	private final Pattern uMinute;
	private final Pattern uHour;
	private final Pattern cCurTrack;
	private final PatternCompat cFF;
	private final PatternCompat cRW;
	private final PatternCompat cFindPlayOpen;
	private final String[] nums;

	VoiceSearchHandler(MainActivityDelegate activity) {
		this.activity = activity;
		Resources res = activity.getContext().getResources();
		aFind = compile(res, R.string.vcmd_action_find);
		aOpen = compile(res, R.string.vcmd_action_open);
		aPause = compile(res, R.string.vcmd_action_pause);
		aStop = compile(res, R.string.vcmd_action_stop);
		aPlay = compile(res, R.string.vcmd_action_play);
		aPlayFavorites = compile(res, R.string.vcmd_action_play_favorites);
		lFolders = compile(res, R.string.vcmd_location_folders);
		lFavorites = compile(res, R.string.vcmd_location_favorites);
		lPlaylists = compile(res, R.string.vcmd_location_playlists);
		lTV = compile(res, R.string.vcmd_location_tv);
		lYoutube = compile(res, R.string.vcmd_location_youtube);
		lBrowser = compile(res, R.string.vcmd_location_browser);
		uSecond = compile(res, R.string.vcmd_time_unit_second);
		uMinute = compile(res, R.string.vcmd_time_unit_minute);
		uHour = compile(res, R.string.vcmd_time_unit_hour);
		cCurTrack = compile(res, R.string.vcmd_cur_track);
		cFF = PatternCompat.compile(res.getString(R.string.vcmd_ff));
		cRW = PatternCompat.compile(res.getString(R.string.vcmd_rw));
		cFindPlayOpen = PatternCompat.compile(res.getString(R.string.vcmd_find_play_open));
		nums = res.getString(R.string.vcmd_nums).split(" ");
	}

	public boolean handle(List<String> cmd) {
		return !cmd.isEmpty() && handle(cmd.get(0));
	}

	public boolean handle(String cmd) {
		cmd = cmd.trim().toLowerCase();

		if (aPlay.matcher(cmd).matches()) {
			activity.getMediaSessionCallback().play().thenRun(activity::goToCurrent);
			return true;
		}
		if (aPause.matcher(cmd).matches()) {
			activity.getMediaSessionCallback().onPause();
			return true;
		}
		if (aStop.matcher(cmd).matches()) {
			activity.getMediaSessionCallback().onStop();
			return true;
		}
		if (aPlayFavorites.matcher(cmd).matches()) {
			activity.showFragment(R.id.favorites_fragment);
			playFavorites(0);
			return true;
		}
		if (cCurTrack.matcher(cmd).matches()) {
			activity.goToCurrent();
			return true;
		}

		Matcher m;
		PatternCompat ff = null;
		if ((m = cFF.matcher(cmd)).matches()) ff = cFF;
		else if ((m = cRW.matcher(cmd)).matches()) ff = cRW;

		if (ff != null) {
			String n = ff.group(m, NUMBER);
			if (n == null) return false;
			String u = ff.group(m, UNIT);
			int time = toNum(n);
			if (time < 0) return false;
			if (matches(uMinute, u)) time *= 60;
			else if (matches(uHour, u)) time *= 3600;
			return activity.getMediaSessionCallback()
					.rewindFastForward(ff == cFF, time, TIME_UNIT_SECOND, 1);
		}

		if ((m = cFindPlayOpen.matcher(cmd)).matches()) {
			String q = cFindPlayOpen.group(m, QUERY);
			if ((q == null) || (q.trim().isEmpty())) return false;
			boolean play = !matches(aFind, cFindPlayOpen.group(m, ACTION))
					&& !matches(aOpen, cFindPlayOpen.group(m, ACTION));
			String location = cFindPlayOpen.group(m, LOCATION);

			if (location == null) {
				MainActivityFragment f = activity.getActiveMainActivityFragment();
				if ((f == null) || !f.isVoiceSearchSupported()) return false;
				f.voiceSearch(q, play);
				return true;
			}

			int fid = ID_NULL;
			if (matches(lFolders, location)) fid = R.id.folders_fragment;
			else if (matches(lFavorites, location)) fid = R.id.favorites_fragment;
			else if (matches(lPlaylists, location)) fid = R.id.playlists_fragment;

			if (fid == ID_NULL) {
				AddonManager amgr = AddonManager.get();
				if (amgr.hasAddon(R.id.tv_fragment) && matches(lTV, location)) fid = R.id.tv_fragment;
				else if (amgr.hasAddon(R.id.youtube_fragment) && matches(lYoutube, location))
					fid = R.id.youtube_fragment;
				else if (amgr.hasAddon(R.id.web_browser_fragment) && matches(lBrowser, location))
					fid = R.id.web_browser_fragment;
			}

			if (fid == ID_NULL) return false;
			activity.showFragment(fid);
			searchInFragment(fid, q, play, 0);
			return true;
		}

		return false;
	}

	private void searchInFragment(@IdRes int id, String q, boolean play, int attempt) {
		if (attempt == 100) {
			Log.e("Failed to perform search in fragment ", id);
			return;
		}
		MainActivityFragment f = activity.getActiveMainActivityFragment();
		if ((f == null) || (f.getFragmentId() != id)) {
			activity.getHandler().post(() -> searchInFragment(id, q, play, attempt + 1));
		} else if ((f.getFragmentId() == R.id.folders_fragment)
				|| (f.getFragmentId() == R.id.playlists_fragment)) {
			MediaLibFragment mf = (MediaLibFragment) f;
			mf.findFolder(q).onSuccess(folder -> {
				if (folder == null) {
					f.voiceSearch(q, play);
				} else {
					if (play) mf.playFolder(folder);
					else mf.openFolder(folder);
				}
			});
		} else {
			f.voiceSearch(q, play);
		}
	}

	private void playFavorites(int attempt) {
		if (attempt == 100) {
			Log.e("Failed to play favorites");
			return;
		}
		MainActivityFragment f = activity.getActiveMainActivityFragment();
		if (f.getFragmentId() == R.id.favorites_fragment) ((FavoritesFragment) f).play();
		else activity.getHandler().post(() -> playFavorites(attempt + 1));
	}

	private static boolean matches(Pattern p, String s) {
		return (s != null) && p.matcher(s).matches();
	}

	private static Pattern compile(Resources res, @StringRes int regex) {
		return Pattern.compile(res.getString(regex));
	}

	private int toNum(String n) {
		if (Character.isDigit(n.charAt(0))) {
			try {
				return Integer.parseInt(n);
			} catch (NumberFormatException ignore) {
			}
		}

		String v1 = n + '|';
		String v2 = '|' + n;
		for (int i = 0; i < nums.length; i++) {
			if (nums[i].equals(n) || nums[i].contains(v1) || nums[i].contains(v2)) return i;
		}
		return -1;
	}
}
