package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.vfs.m3u.M3uFile.URL;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.net.Uri;

import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
class KnownProviders {

	static void configure(PreferenceStore ps) {
		String playlistUrl = ps.getStringPref(URL);
		if (isNullOrBlank(playlistUrl)) return;
		String host = Uri.parse(playlistUrl).getHost();
		int i1 = host.lastIndexOf('.');

		if (i1 != -1) {
			int i2 = host.lastIndexOf('.', i1 - 1);
			host = (i2 == -1) ? host.substring(0, i1) : host.substring(i2 + 1, i1);
		} else {
			return;
		}

		switch (host) {
			case "edem":
			case "iedem":
			case "edemtv":
			case "ilooktv":
			case "ilook-tv":
				configure(ps, "http://epg.it999.ru/epg2.xml.gz", TvM3uFile.CATCHUP_TYPE_SHIFT);
		}
	}

	static void configure(PreferenceStore ps, String epg, int catchup) {
		configure(ps, epg, catchup, null);
	}

	static void configure(PreferenceStore ps, String epg, int catchup, String q) {
		try (PreferenceStore.Edit e = ps.editPreferenceStore()) {
			if (isNullOrBlank(ps.getStringPref(TvM3uFile.EPG_URL))) {
				e.setStringPref(TvM3uFile.EPG_URL, epg);
			}
			if(ps.getIntPref(TvM3uFile.CATCHUP_TYPE) == TvM3uFile.CATCHUP_TYPE_AUTO) {
				e.setIntPref(TvM3uFile.CATCHUP_TYPE, catchup);
			}
			if((q != null) && isNullOrBlank(ps.getStringPref(TvM3uFile.CATCHUP_QUERY))) {
				e.setStringPref(TvM3uFile.CATCHUP_QUERY, epg);
			}
		}
	}
}
