package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

/**
 * YouTube URL helpers shared by the page and the addon. Plain Java, without android.net.Uri, so they
 * can be unit tested.
 */
final class YoutubeUrls {

	private YoutubeUrls() {
	}

	// A video position reported by the page
	record Progress(int sec, String url) {
	}

	// The same pages as isPreview() in YoutubeWebView.attachListeners(): keep the two in sync
	static boolean isVideoUrl(@Nullable String url) {
		String p = path(url);
		return (p != null) && (p.startsWith("/watch") || p.startsWith("/shorts") ||
				p.startsWith("/live") || p.startsWith("/embed"));
	}

	// The v parameter of a /watch page, the path of any other page, or "" if there is none
	static String videoId(@Nullable String url) {
		String p = path(url);
		if (p == null) return "";
		if (!"/watch".equals(p)) return p;
		String id = param(url, "v");
		return (id == null) ? "" : id;
	}

	// YouTube's start time parameter, replacing any there is, for a /watch page and a position of 1s or more
	static String withStartTime(String url, long posMs) {
		if ((posMs < 1000L) || !"/watch".equals(path(url))) return url;
		int f = url.indexOf('#');
		String fragment = (f < 0) ? "" : url.substring(f);
		String base = (f < 0) ? url : url.substring(0, f);
		int q = base.indexOf('?');
		StringBuilder b = new StringBuilder((q < 0) ? base : base.substring(0, q));
		char sep = '?';

		if (q >= 0) {
			for (String p : base.substring(q + 1).split("&")) {
				if (p.isEmpty() || paramName(p).equals("t")) continue;
				b.append(sep).append(p);
				sep = '&';
			}
		}

		return b.append(sep).append("t=").append(posMs / 1000L).append('s').append(fragment).toString();
	}

	// data: "<position in seconds>|<video url>", or null if it's not
	@Nullable
	static Progress parseProgress(@Nullable String data) {
		int idx = (data == null) ? -1 : data.indexOf('|');
		if (idx <= 0) return null;
		try {
			return new Progress(Integer.parseInt(data.substring(0, idx)), data.substring(idx + 1));
		} catch (NumberFormatException err) {
			return null;
		}
	}

	@Nullable
	private static String path(@Nullable String url) {
		if (url == null) return null;
		int end = url.length();
		int i = url.indexOf('#');
		if (i >= 0) end = i;
		i = url.indexOf('?');
		if ((i >= 0) && (i < end)) end = i;
		String s = url.substring(0, end);
		i = s.indexOf("://");
		if (i < 0) return s.startsWith("/") ? s : null;
		i = s.indexOf('/', i + 3);
		return (i < 0) ? "" : s.substring(i);
	}

	@Nullable
	private static String param(String url, String name) {
		int f = url.indexOf('#');
		String base = (f < 0) ? url : url.substring(0, f);
		int q = base.indexOf('?');
		if (q < 0) return null;
		for (String p : base.substring(q + 1).split("&")) {
			if (!paramName(p).equals(name)) continue;
			int eq = p.indexOf('=');
			return (eq < 0) ? "" : p.substring(eq + 1);
		}
		return null;
	}

	private static String paramName(String param) {
		int eq = param.indexOf('=');
		return (eq < 0) ? param : param.substring(0, eq);
	}
}
