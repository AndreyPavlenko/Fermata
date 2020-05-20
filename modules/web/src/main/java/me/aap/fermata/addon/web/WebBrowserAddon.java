package me.aap.fermata.addon.web;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
	private static final Pref<Supplier<String>> LAST_URL = Pref.s("LAST_URL", "http://google.com");
	private static final Pref<BooleanSupplier> FORCE_DARK = Pref.b("FORCE_DARK", false);
	private static final Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS");
	private final SharedPreferenceStore preferenceStore;

	public WebBrowserAddon() {
		preferenceStore = SharedPreferenceStore.create(App.get().getSharedPreferences("web", Context.MODE_PRIVATE));
	}

	@Override
	public int getNavId() {
		return me.aap.fermata.R.id.web_browser_fragment;
	}

	@Nullable
	@Override
	public ActivityFragment createFragment(int id) {
		return (id == me.aap.fermata.R.id.web_browser_fragment) ? new WebBrowserFragment() : null;
	}

	@Override
	public void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
		set.addBooleanPref(o -> {
			o.store = getPreferenceStore();
			o.pref = getForceDarkPref();
			o.title = R.string.force_dark;
			o.visibility = visibility;
		});
	}

	public SharedPreferenceStore getPreferenceStore() {
		return preferenceStore;
	}

	public Pref<BooleanSupplier> getForceDarkPref() {
		return FORCE_DARK;
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
