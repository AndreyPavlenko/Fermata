package me.aap.fermata.addon.web;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.utils.app.App;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserAddon implements FermataAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(WebBrowserAddon.class.getName());
	private static final Pref<Supplier<String>> LAST_URL = Pref.s("LAST_URL", "http://google.com");
	private static final Pref<BooleanSupplier> FORCE_DARK = Pref.b("FORCE_DARK", false);
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
	private final SharedPreferenceStore preferenceStore;

	public WebBrowserAddon() {
		preferenceStore = SharedPreferenceStore.create(App.get().getSharedPreferences("web", Context.MODE_PRIVATE));
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
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = getForceDarkPref();
			o.title = R.string.force_dark;
			o.visibility = visibility;
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
		return preferenceStore;
	}

	public Pref<BooleanSupplier> getForceDarkPref() {
		return FORCE_DARK;
	}

	public Pref<Supplier<String>> getUserAgentPref() {
		return USER_AGENT;
	}

	public Pref<Supplier<String>> getUserAgentDesktopPref() {
		return USER_AGENT_DESKTOP;
	}

	public boolean isForceDark() {
		return getPreferenceStore().getBooleanPref(getForceDarkPref());
	}

	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return DESKTOP_VERSION;
	}

	public boolean isDesktopVersion() {
		return getPreferenceStore().getBooleanPref(getDesktopVersionPref());
	}

	public void setDesktopVersion(boolean v) {
		getPreferenceStore().applyBooleanPref(DESKTOP_VERSION, v);
	}

	Map<String, String> getBookmarks() {
		String[] p = getPreferenceStore().getStringArrayPref(BOOKMARKS);
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

		getPreferenceStore().applyStringArrayPref(BOOKMARKS, p);
	}

	String getLastUrl() {
		return getPreferenceStore().getStringPref(LAST_URL);
	}

	void setLastUrl(String url) {
		getPreferenceStore().applyStringPref(LAST_URL, url);
	}
}
