package me.aap.fermata.addon.web;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.utils.app.App;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserAddon implements FermataFragmentAddon, SharedPreferenceStore {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(WebBrowserAddon.class.getName());
	private static final Pref<Supplier<String>> LAST_URL = Pref.s("LAST_URL", "http://google.com");
	public static final int DARK_MODE_DISABLED = 0;
	public static final int DARK_MODE_ENABLED = 1;
	public static final int DARK_MODE_AUTO = 2;
	private static final Pref<IntSupplier> DARK_MODE = Pref.i("DARK_MODE", DARK_MODE_AUTO);
	private static final Pref<Supplier<String>> USER_AGENT = Pref.s("USER_AGENT",
			"Mozilla/5.0 (Linux; Android {ANDROID_VERSION}) " +
					"AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) " +
					"Chrome/{CHROME_VERSION} Mobile Safari/{WEBKIT_VERSION}");
	private static final Pref<Supplier<String>> USER_AGENT_DESKTOP = Pref.s("USER_AGENT_DESKTOP",
			"Mozilla/5.0 (X11; Linux x86_64) " +
					"AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) " +
					"Chrome/{CHROME_VERSION} Safari/{WEBKIT_VERSION}");
	private static final Pref<BooleanSupplier> DESKTOP_VERSION = Pref.b("DESKTOP_VERSION", false);
	private static final Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS");
	private final SharedPreferences prefs;

	public WebBrowserAddon() {
		prefs = App.get().getSharedPreferences("web", Context.MODE_PRIVATE);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.web_browser_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new WebBrowserFragment();
	}

	@Override
	public void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
		set.addListPref(o -> {
			o.store = getPreferenceStore();
			o.pref = getForceDarkPref();
			o.title = R.string.force_dark;
			o.subtitle = R.string.force_dark_sub;
			o.visibility = visibility;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.force_dark_disabled, R.string.force_dark_enabled, R.string.force_dark_auto};
		});

		if (getClass() == WebBrowserAddon.class) {
			set.addStringPref(o -> {
				o.store = getPreferenceStore();
				o.pref = getUserAgentPref();
				o.title = R.string.user_agent;
				o.stringHint = o.pref.getDefaultValue().get();
				o.visibility = visibility;
				o.maxLines = 3;
			});
			set.addStringPref(o -> {
				o.store = getPreferenceStore();
				o.pref = getUserAgentDesktopPref();
				o.title = R.string.user_agent_desktop;
				o.stringHint = o.pref.getDefaultValue().get();
				o.visibility = visibility;
				o.maxLines = 3;
			});
		}
	}

	public SharedPreferenceStore getPreferenceStore() {
		return this;
	}

	private Collection<ListenerRef<Listener>> listeners;

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return prefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return (listeners != null) ? listeners : (listeners = new LinkedList<>());
	}

	public Pref<IntSupplier> getForceDarkPref() {
		return DARK_MODE;
	}

	public Pref<Supplier<String>> getUserAgentPref() {
		return USER_AGENT;
	}

	public Pref<Supplier<String>> getUserAgentDesktopPref() {
		return USER_AGENT_DESKTOP;
	}

	public String getUserAgentDesktop() {
		Pref<Supplier<String>> p = getUserAgentDesktopPref();
		String ua = getPreferenceStore().getStringPref(p);
		return TextUtils.isNullOrBlank(ua) ? p.getDefaultValue().get() : ua;
	}

	public String getUserAgent() {
		Pref<Supplier<String>> p = getUserAgentPref();
		String ua = getPreferenceStore().getStringPref(p);
		return TextUtils.isNullOrBlank(ua) ? p.getDefaultValue().get() : ua;
	}

	public boolean isDisableDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 0;
	}

	public boolean isForceDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 1;
	}

	public boolean isAutoDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 2;
	}

	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return DESKTOP_VERSION;
	}

	public Pref<Supplier<String[]>> getBookmarksPref() {
		return BOOKMARKS;
	}

	public boolean isDesktopVersion() {
		return getPreferenceStore().getBooleanPref(getDesktopVersionPref());
	}

	public void setDesktopVersion(boolean v) {
		getPreferenceStore().applyBooleanPref(DESKTOP_VERSION, v);
	}

	Map<String, String> getBookmarks() {
		String[] p = getPreferenceStore().getStringArrayPref(getBookmarksPref());
		if (p.length == 0) return Collections.emptyMap();

		Map<String, String> m = new LinkedHashMap<>(p.length);
		for (int i = 0; i < p.length; i++) {
			m.put(p[i], p[++i]);
		}
		return m;
	}

	void addBookmark(String name, String url) {
		Map<String, String> m = getBookmarks();

		if (m.isEmpty()) {
			setBookmarks(Collections.singletonMap(url, name));
		} else {
			m.put(url, name);
			setBookmarks(m);
		}
	}

	void removeBookmark(String url) {
		Map<String, String> m = getBookmarks();

		if (!m.isEmpty()) {
			m.remove(url);
			setBookmarks(m);
		}
	}

	void setBookmarks(Map<String, String> m) {
		String[] p = new String[m.size() * 2];
		int i = 0;

		for (Map.Entry<String, String> e : m.entrySet()) {
			p[i++] = e.getKey();
			p[i++] = e.getValue();
		}

		getPreferenceStore().applyStringArrayPref(getBookmarksPref(), p);
	}

	String getLastUrl() {
		return getPreferenceStore().getStringPref(LAST_URL);
	}

	void setLastUrl(String url) {
		getPreferenceStore().applyStringPref(LAST_URL, url);
	}
}
