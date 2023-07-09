package me.aap.fermata.ui.activity;

import static me.aap.fermata.media.pref.PlaybackControlPrefs.TIME_UNIT_SECOND;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.VoiceCommand.ACTION_CHAT;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.log.Log;
import me.aap.utils.text.PatternCompat;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
class VoiceCommandHandler {
	private static final String ACTION = "A";
	private static final String LOCATION = "L";
	private static final String QUERY = "Q";
	private static final String NUMBER = "N";
	private static final String UNIT = "U";
	private final MainActivityDelegate activity;
	private String lang;
	private Pattern aFind;
	private Pattern aOpen;
	private Pattern aPause;
	private Pattern aStop;
	private Pattern aPlay;
	private Pattern aPlayFavorites;
	private Pattern aSubOn;
	private Pattern aSubOff;
	private Pattern aSubChange;
	private Pattern aAudioChange;
	private Pattern lFolders;
	private Pattern lFavorites;
	private Pattern lPlaylists;
	private Pattern lTV;
	private Pattern lYoutube;
	private Pattern lBrowser;
	private Pattern uMinute;
	private Pattern uHour;
	private Pattern cCurTrack;
	private PatternCompat cFF;
	private PatternCompat cRW;
	private PatternCompat cFindPlayOpen;
	private PatternCompat cChat;
	private String[] nums;
	private Map<String, String> subst = Collections.emptyMap();

	VoiceCommandHandler(MainActivityDelegate activity) {
		this.activity = activity;
	}

	private void init() {
		String lang = activity.getPrefs().getVoiceControlLang(activity);
		if (lang.equals(this.lang)) return;

		Context ctx = activity.getContext();
		Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
		cfg.setLocale(Locale.forLanguageTag(lang));
		Resources res = ctx.createConfigurationContext(cfg).getResources();
		aFind = compile(res, R.string.vcmd_action_find);
		aOpen = compile(res, R.string.vcmd_action_open);
		aPause = compile(res, R.string.vcmd_action_pause);
		aStop = compile(res, R.string.vcmd_action_stop);
		aPlay = compile(res, R.string.vcmd_action_play);
		aPlayFavorites = compile(res, R.string.vcmd_action_play_favorites);
		aSubOn = compile(res, R.string.vcmd_action_sub_on);
		aSubOff = compile(res, R.string.vcmd_action_sub_off);
		aSubChange = compile(res, R.string.vcmd_action_sub_change);
		aAudioChange = compile(res, R.string.vcmd_action_audio_change);
		lFolders = compile(res, R.string.vcmd_location_folders);
		lFavorites = compile(res, R.string.vcmd_location_favorites);
		lPlaylists = compile(res, R.string.vcmd_location_playlists);
		lTV = compile(res, R.string.vcmd_location_tv);
		lYoutube = compile(res, R.string.vcmd_location_youtube);
		lBrowser = compile(res, R.string.vcmd_location_browser);
		uMinute = compile(res, R.string.vcmd_time_unit_minute);
		uHour = compile(res, R.string.vcmd_time_unit_hour);
		cCurTrack = compile(res, R.string.vcmd_cur_track);
		cFF = PatternCompat.compile(res.getString(R.string.vcmd_ff));
		cRW = PatternCompat.compile(res.getString(R.string.vcmd_rw));
		cFindPlayOpen = PatternCompat.compile(res.getString(R.string.vcmd_find_play_open));
		cChat = PatternCompat.compile(res.getString(R.string.vcmd_chat));
		nums = res.getString(R.string.vcmd_nums).split(" ");
		updateWordSubst();
	}

	public boolean handle(List<String> cmd) {
		return !cmd.isEmpty() && handle(cmd.get(0));
	}

	public boolean handle(String cmd) {
		init();
		cmd = subst(cmd);

		AddonManager amgr = AddonManager.get();

		if (amgr.hasAddon(R.id.chat_addon)) {
			Matcher m = cChat.matcher(cmd);
			if (m.matches()) {
				MainActivityFragment f = activity.showFragment(R.id.chat_addon);
				f.voiceCommand(new VoiceCommand(cChat.group(m, QUERY), ACTION_CHAT));
				return true;
			}
		}

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

		MediaSessionCallback cb = activity.getMediaSessionCallback();
		MediaEngineManager mgr = cb.getEngineManager();
		MediaEngine eng;

		if (mgr.isVlcPlayerSupported() && ((eng = cb.getEngine()) != null)) {
			if (aSubOn.matcher(cmd).matches()) {
				if (eng.getCurrentSubtitleStreamInfo() != null) return true;
				eng.getSubtitleStreamInfo().main().onSuccess(sub -> {
					if (!sub.isEmpty()) eng.setCurrentSubtitleStream(sub.get(0));
				});
				return true;
			}
			if (aSubOff.matcher(cmd).matches()) {
				eng.setCurrentSubtitleStream(null);
				return true;
			}
			if (aSubChange.matcher(cmd).matches()) {
				eng.getSubtitleStreamInfo().main().onSuccess(sub -> {
					if (!sub.isEmpty()) return;
					var cur = eng.getCurrentSubtitleStreamInfo();
					if (cur == null) eng.setCurrentSubtitleStream(sub.get(0));
					else eng.setCurrentSubtitleStream(next(sub, cur));
				});
				return true;
			}
			if (aAudioChange.matcher(cmd).matches()) {
				eng.setCurrentAudioStream(next(eng.getAudioStreamInfo(), eng.getCurrentAudioStreamInfo()));
				return true;
			}
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
			int action = matches(aFind, cFindPlayOpen.group(m, ACTION)) ? VoiceCommand.ACTION_FIND :
					matches(aOpen, cFindPlayOpen.group(m, ACTION)) ? VoiceCommand.ACTION_OPEN :
							VoiceCommand.ACTION_PLAY;
			VoiceCommand vcmd = new VoiceCommand(q, action);
			String location = cFindPlayOpen.group(m, LOCATION);

			if (location == null) {
				MainActivityFragment f = activity.getActiveMainActivityFragment();
				if ((f == null) || !f.isVoiceCommandsSupported()) return false;
				f.voiceCommand(vcmd);
				return true;
			}

			int fid = ID_NULL;
			if (matches(lFolders, location)) fid = R.id.folders_fragment;
			else if (matches(lFavorites, location)) fid = R.id.favorites_fragment;
			else if (matches(lPlaylists, location)) fid = R.id.playlists_fragment;

			if (fid == ID_NULL) {
				if (amgr.hasAddon(R.id.tv_fragment) && matches(lTV, location)) fid = R.id.tv_fragment;
				else if (amgr.hasAddon(R.id.youtube_fragment) && matches(lYoutube, location))
					fid = R.id.youtube_fragment;
				else if (amgr.hasAddon(R.id.web_browser_fragment) && matches(lBrowser, location))
					fid = R.id.web_browser_fragment;
			}

			if (fid == ID_NULL) return false;
			activity.showFragment(fid);
			searchInFragment(fid, vcmd, 0);
			return true;
		}

		return false;
	}

	private void searchInFragment(@IdRes int id, VoiceCommand cmd, int attempt) {
		if (attempt == 100) {
			Log.e("Failed to perform search in fragment ", id);
			return;
		}
		MainActivityFragment f = activity.getActiveMainActivityFragment();
		if ((f == null) || (f.getFragmentId() != id)) {
			activity.post(() -> searchInFragment(id, cmd, attempt + 1));
		} else if ((f.getFragmentId() == R.id.folders_fragment) ||
				(f.getFragmentId() == R.id.playlists_fragment)) {
			MediaLibFragment mf = (MediaLibFragment) f;
			mf.findFolder(cmd.getQuery()).onSuccess(folder -> {
				if (folder == null) {
					f.voiceCommand(cmd);
				} else {
					if (cmd.isPlay()) mf.playFolder(folder);
					else mf.openFolder(folder);
				}
			});
		} else {
			f.voiceCommand(cmd);
		}
	}

	private void playFavorites(int attempt) {
		if (attempt == 100) {
			Log.e("Failed to play favorites");
			return;
		}
		MainActivityFragment f = activity.getActiveMainActivityFragment();
		if (f instanceof FavoritesFragment) ((FavoritesFragment) f).play();
		else activity.post(() -> playFavorites(attempt + 1));
	}

	public void updateWordSubst() {
		String pref = activity.getPrefs().getStringPref(VOICE_CONTROL_SUBST);
		Map<String, String> subst = new HashMap<>();
		try (BufferedReader r = new BufferedReader(new StringReader(pref))) {
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				int idx = l.indexOf(':');
				if (idx < 0) continue;
				subst.put(l.substring(0, idx).trim().toLowerCase(),
						l.substring(idx + 1).trim().toLowerCase());
			}
		} catch (IOException ignore) {
		}
		this.subst = subst.isEmpty() ? Collections.emptyMap() : subst;
	}

	private String subst(String cmd) {
		Map<String, String> subst = this.subst;
		int start = -1;

		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			for (int i = 0, n = cmd.codePointCount(0, cmd.length()); i < n; i++) {
				int c = cmd.codePointAt(i);

				if (Character.isWhitespace(c)) {
					if (start == -1) continue;
					if (!subst.isEmpty()) {
						String r = subst.get(b.substring(start, b.length()));
						if (r != null) b.replace(start, b.length(), r);
					}
					start = -1;
				} else {
					if (start == -1) {
						if (b.length() != 0) b.append(' ');
						start = b.length();
					}
					b.appendCodePoint(Character.toLowerCase(c));
				}
			}
			if ((start != -1) && !subst.isEmpty()) {
				String r = subst.get(b.substring(start, b.length()));
				if (r != null) b.replace(start, b.length(), r);
			}

			return b.toString();
		}
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

	private static <T> T next(List<T> l, T t) {
		int idx = l.indexOf(t);
		return (idx < 0) ? t : l.get((++idx == l.size()) ? 0 : idx);
	}
}
