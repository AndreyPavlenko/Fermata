package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_DAYS;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_QUERY;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_APPEND;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_AUTO;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_DEFAULT;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_FILE_AGE;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_SHIFT;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.EPG_URL;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.LOGO_PREFER_EPG;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.LOGO_URL;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.STREAM_CACHE_HOURS;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.XTREAM_PASS;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.XTREAM_URL;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.XTREAM_USER;
import static me.aap.fermata.vfs.m3u.M3uFile.NAME;
import static me.aap.fermata.vfs.m3u.M3uFile.URL;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;

import android.net.Uri;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystemProvider;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFileSystemProvider extends M3uFileSystemProvider {

	@Override
	public FutureSupplier<TvM3uFile> select(MainActivityDelegate a, List<? extends VirtualFileSystem> fs) {
		PreferenceStore ps = PrefsHolder.instance;
		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();
			return load(ps, TvM3uFileSystem.getInstance()).cast();
		});
	}

	public FutureSupplier<Boolean> edit(MainActivityDelegate a, TvM3uFile f) {
		BasicPreferenceStore ps = new BasicPreferenceStore();
		String url = f.getUrl();
		String epgUrl = f.getEpgUrl();
		float shift = f.getEpgShift();
		XtreamData x = XtreamData.parse(url);
		String xtreamUrl = trimToNull(f.getXtreamUrl());
		String xtreamUser = trimToNull(f.getXtreamUser());
		String xtreamPass = trimToNull(f.getXtreamPass());
		if (xtreamUrl == null) xtreamUrl = x.url;
		if (xtreamUser == null) xtreamUser = x.user;
		if (xtreamPass == null) xtreamPass = x.pass;

		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			e.setStringPref(NAME, f.getName());
			e.setStringPref(URL, f.getUrl());
			e.setStringPref(EPG_URL, f.getEpgUrl());
			e.setStringPref(XTREAM_URL, xtreamUrl);
			e.setStringPref(XTREAM_USER, xtreamUser);
			e.setStringPref(XTREAM_PASS, xtreamPass);
			e.setIntPref(STREAM_CACHE_HOURS, f.getStreamCacheHours());
			e.setBooleanPref(LOGO_PREFER_EPG, f.isPreferEpgLogo());
			e.setFloatPref(EPG_SHIFT, f.getEpgShift());
			e.setIntPref(CATCHUP_TYPE, f.getCatchupType());
			e.setIntPref(CATCHUP_DAYS, f.getCatchupDays());
			e.setStringPref(CATCHUP_QUERY, f.getCatchupQuery());
			e.setStringPref(LOGO_URL, f.getLogoUrl());
			e.setStringPref(AGENT, f.getUserAgent());
			e.setIntPref(RESP_TIMEOUT, f.getResponseTimeout());
		}

		return requestPrefs(a, ps).thenRun(ps::removeBroadcastListeners).map(ok -> {
			if (!ok) return false;
			setPrefs(ps, f);

			if (!Objects.equals(url, f.getUrl())
					|| !Objects.equals(epgUrl, f.getEpgUrl())
					|| (shift != f.getEpgShift())) {
				Log.d("TV source has been modified - clearing stamps.");
				f.clearStamps();
			}

			return true;
		});
	}

	private FutureSupplier<Boolean> requestPrefs(MainActivityDelegate a, PreferenceStore ps) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceSet sub;

		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = NAME;
			o.title = me.aap.fermata.R.string.m3u_playlist_name;
		});
		prefs.addFilePref(o -> {
			o.store = ps;
			o.pref = URL;
			o.mode = FilePickerFragment.FILE;
			o.title = me.aap.fermata.R.string.m3u_playlist_location;
			o.stringHint = a.getString(me.aap.fermata.R.string.m3u_playlist_location_hint);
		});
		sub = prefs.subSet(o -> o.title = R.string.xtream_codes);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = XTREAM_URL;
			o.title = R.string.xtream_server_url;
			o.stringHint = "http://example.com:8080";
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = XTREAM_USER;
			o.title = R.string.xtream_username;
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = XTREAM_PASS;
			o.title = R.string.xtream_password;
		});

		sub = prefs.subSet(o -> o.title = R.string.epg);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = EPG_URL;
			o.title = R.string.epg_url;
			o.stringHint = "http://example.com/epg.xml.gz";
		});
		sub.addBooleanPref(o -> {
			o.store = ps;
			o.pref = LOGO_PREFER_EPG;
			o.title = R.string.logo_prefer_epg;
		});
		sub.addFloatPref(o -> {
			o.store = ps;
			o.pref = EPG_SHIFT;
			o.seekMin = -12;
			o.seekMax = 12;
			o.title = R.string.epg_time_shift;
		});

		sub = prefs.subSet(o -> o.title = R.string.catchup);
		sub.addListPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_TYPE;
			o.title = R.string.catchup_type;
			o.subtitle = R.string.catchup_type_cur;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.catchup_type_auto, R.string.catchup_type_append,
					R.string.catchup_type_default, R.string.catchup_type_shift, R.string.catchup_type_flussonic};
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_DAYS;
			o.seekMax = 30;
			o.title = R.string.catchup_days;
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) != CATCHUP_TYPE_AUTO);
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_QUERY;
			o.title = R.string.catchup_query;
			o.stringHint = "?timeshift=${start}&timenow=${timestamp}";
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) == CATCHUP_TYPE_APPEND);
		});
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = CATCHUP_QUERY;
			o.title = R.string.catchup_query;
			o.stringHint = "http://example.com/stream1_${offset}.m3u8";
			o.visibility = new PrefCondition<>(ps, CATCHUP_TYPE,
					p -> ps.getIntPref(CATCHUP_TYPE) == CATCHUP_TYPE_DEFAULT);
		});

		sub = prefs.subSet(o -> o.title = R.string.logo);
		sub.addFilePref(o -> {
			o.store = ps;
			o.pref = LOGO_URL;
			o.mode = FilePickerFragment.FILE;
			o.title = R.string.logo_location;
			o.stringHint = a.getString(R.string.logo_location_hint);
		});

		sub = prefs.subSet(o -> o.title = R.string.connection_settings);
		sub.addStringPref(o -> {
			o.store = ps;
			o.pref = AGENT;
			o.title = me.aap.fermata.R.string.m3u_playlist_agent;
			o.stringHint = "Fermata/" + BuildConfig.VERSION_NAME;
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = RESP_TIMEOUT;
			o.title = me.aap.fermata.R.string.m3u_playlist_timeout;
		});
		sub.addIntPref(o -> {
			o.store = ps;
			o.pref = STREAM_CACHE_HOURS;
			o.seekMax = 24 * 30;
			o.title = R.string.stream_list_cache_hours;
		});

		return requestPrefs(a, prefs, ps);
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		boolean hasXtream = hasXtreamInput(ps);
		boolean useXtream = hasXtreamCredentials(ps);
		if (hasXtream && !useXtream) return false;
		if (useXtream) {
			String name = trimToNull(ps.getStringPref(NAME));
			if (name == null) return false;
			String url = normalizeXtreamUrl(ps.getStringPref(XTREAM_URL));
			if ((url == null) || !(url.startsWith("http://") || url.startsWith("https://"))) return false;
		} else if (!super.validate(ps)) {
			return false;
		}
		String q;

		switch (ps.getIntPref(CATCHUP_TYPE)) {
			case CATCHUP_TYPE_APPEND:
				q = ps.getStringPref(CATCHUP_QUERY);
				return (q != null) && !TextUtils.isBlank(q);
			case CATCHUP_TYPE_DEFAULT:
				q = ps.getStringPref(CATCHUP_QUERY);
				return (q != null) && !TextUtils.isBlank(q) &&
						(q.startsWith("http://") || q.startsWith("https://"));
			default:
				return true;
		}
	}

	@Override
	protected void setPrefs(PreferenceStore ps, M3uFile m3u) {
		TvM3uFile f = (TvM3uFile) m3u;
		String xtreamUrl = trimToNull(ps.getStringPref(XTREAM_URL));
		String xtreamUser = trimToNull(ps.getStringPref(XTREAM_USER));
		String xtreamPass = trimToNull(ps.getStringPref(XTREAM_PASS));
		String playlistUrl;
		String xtreamEpg = null;

		if (hasXtreamCredentials(xtreamUrl, xtreamUser, xtreamPass)) {
			playlistUrl = buildXtreamPlaylistUrl(xtreamUrl, xtreamUser, xtreamPass);
			xtreamEpg = buildXtreamEpgUrl(xtreamUrl, xtreamUser, xtreamPass);
		} else {
			playlistUrl = trimToNull(ps.getStringPref(URL));
		}

		KnownProviders.configure(ps, playlistUrl);
		super.setPrefs(ps, f);
		if (playlistUrl != null) f.setUrl(playlistUrl);
		f.setVideo(true);
		f.setXtreamUrl(xtreamUrl);
		f.setXtreamUser(xtreamUser);
		f.setXtreamPass(xtreamPass);
		String epgUrl = trimToNull(ps.getStringPref(EPG_URL));
		if ((epgUrl == null) && (xtreamEpg != null)) epgUrl = xtreamEpg;
		f.setEpgUrl(epgUrl);
		f.setEpgShift(ps.getFloatPref(EPG_SHIFT));
		f.setCatchupQuery(ps.getStringPref(CATCHUP_QUERY));
		f.setCatchupType(ps.getIntPref(CATCHUP_TYPE));
		f.setCatchupDays(ps.getIntPref(CATCHUP_DAYS));
		f.setLogoUrl(ps.getStringPref(LOGO_URL));
		f.setPreferEpgLogo(ps.getBooleanPref(LOGO_PREFER_EPG));
		f.setEpgMaxAge(EPG_FILE_AGE);
		f.setResponseTimeout(ps.getIntPref(RESP_TIMEOUT));
		f.setStreamCacheHours(ps.getIntPref(STREAM_CACHE_HOURS));
	}

	public static void removeSource(TvM3uFile f) {
		Log.d("Removing TV source ", f);
		f.cleanUp();
	}

	protected String getTitle(MainActivityDelegate a) {
		return a.getString(R.string.add_tv_source);
	}

	private static boolean hasXtreamInput(PreferenceStore ps) {
		return hasXtreamInput(trimToNull(ps.getStringPref(XTREAM_URL)),
				trimToNull(ps.getStringPref(XTREAM_USER)),
				trimToNull(ps.getStringPref(XTREAM_PASS)));
	}

	private static boolean hasXtreamInput(String url, String user, String pass) {
		return (url != null) || (user != null) || (pass != null);
	}

	private static boolean hasXtreamCredentials(PreferenceStore ps) {
		return hasXtreamCredentials(trimToNull(ps.getStringPref(XTREAM_URL)),
				trimToNull(ps.getStringPref(XTREAM_USER)),
				trimToNull(ps.getStringPref(XTREAM_PASS)));
	}

	private static boolean hasXtreamCredentials(String url, String user, String pass) {
		return (url != null) && (user != null) && (pass != null);
	}

	private static String buildXtreamPlaylistUrl(String url, String user, String pass) {
		String base = normalizeXtreamUrl(url);
		return base + "/get.php?username=" + Uri.encode(user) + "&password=" + Uri.encode(pass) +
				"&type=m3u_plus&output=ts";
	}

	private static String buildXtreamEpgUrl(String url, String user, String pass) {
		String base = normalizeXtreamUrl(url);
		return base + "/xmltv.php?username=" + Uri.encode(user) + "&password=" + Uri.encode(pass);
	}

	private static String normalizeXtreamUrl(String url) {
		String u = trimToNull(url);
		if ((u != null) && !u.contains("://")) u = "http://" + u;
		while ((u != null) && u.endsWith("/")) u = u.substring(0, u.length() - 1);
		return u;
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		value = value.trim();
		return value.isEmpty() ? null : value;
	}

	private static final class XtreamData {
		final String url;
		final String user;
		final String pass;

		XtreamData(String url, String user, String pass) {
			this.url = url;
			this.user = user;
			this.pass = pass;
		}

		static XtreamData parse(String playlistUrl) {
			String url = trimToNull(playlistUrl);
			if ((url == null) || !(url.startsWith("http://") || url.startsWith("https://"))) {
				return new XtreamData(null, null, null);
			}

			Uri uri = Uri.parse(url);
			String user = trimToNull(uri.getQueryParameter("username"));
			String pass = trimToNull(uri.getQueryParameter("password"));
			if ((user == null) || (pass == null)) return new XtreamData(null, null, null);

			String path = uri.getPath();
			if (path == null) return new XtreamData(null, null, null);
			int idx = path.lastIndexOf("/get.php");
			if (idx < 0) return new XtreamData(null, null, null);
			String authority = uri.getEncodedAuthority();
			if (authority == null) return new XtreamData(null, null, null);
			String basePath = path.substring(0, idx);
			StringBuilder base = new StringBuilder();
			base.append(uri.getScheme()).append("://").append(authority);
			if (!basePath.isEmpty()) base.append(basePath);
			return new XtreamData(base.toString(), user, pass);
		}
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
