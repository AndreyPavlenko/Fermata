package me.aap.fermata.addon;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.event.BasicEventBroadcaster;
import me.aap.utils.log.Log;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class AddonManager extends BasicEventBroadcaster<AddonManager.Listener>
		implements PreferenceStore.Listener {
	private static final String CHANNEL_ID = "fermata.addon.install";
	private final Map<String, FermataAddon> addons = new HashMap<>();

	public AddonManager(PreferenceStore store) {
		for (AddonInfo i : BuildConfig.ADDONS) {
			if (!store.getBooleanPref(i.enabledPref)) continue;

			try {
				FermataAddon a = (FermataAddon) Class.forName(i.className).newInstance();
				addons.put(i.className, a);
			} catch (Exception ignore) {
			}
		}

		store.addBroadcastListener(this);
	}

	public static AddonManager get() {
		return FermataApplication.get().getAddonManager();
	}

	@Nullable
	public FermataAddon getAddon(String className) {
		return addons.get(className);
	}

	@Nullable
	public <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) addons.get(c.getName());
	}

	public Collection<FermataAddon> getAddons() {
		return addons.values();
	}

	@Nullable
	public ActivityFragment createFragment(@IdRes int id) {
		for (FermataAddon a : getAddons()) {
			ActivityFragment f = a.createFragment(id);
			if (f != null) return f;
		}
		return null;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		for (AddonInfo i : BuildConfig.ADDONS) {
			if (prefs.contains(i.enabledPref)) {
				if (store.getBooleanPref(i.enabledPref)) {
					install(i, true);
				} else {
					uninstall(i);
				}
			}
		}
	}

	private void install(AddonInfo i, boolean installModule) {
		if (!addons.containsKey(i.className)) {
			try {
				FermataAddon a = (FermataAddon) Class.forName(i.className).newInstance();
				addons.put(i.className, a);
				fireBroadcastEvent(c -> c.addonChanged(this, i, true));
				return;
			} catch (Exception ignore) {
			}

			if (!installModule) return;

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon,
					i.moduleName, null, MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = new DynamicModuleInstaller(a);
				inst.install(i.moduleName).onSuccess(v -> fireBroadcastEvent(c -> {
					Log.i("Module installed: ", i.moduleName);
					install(i, false);
				}));
			});
		}
	}

	private void uninstall(AddonInfo i) {
		if (addons.remove(i.className) != null) {
			fireBroadcastEvent(c -> c.addonChanged(this, i, false));

			for (AddonInfo ai : BuildConfig.ADDONS) {
				if (ai.moduleName.equals(i.moduleName)) return;
			}

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon,
					i.moduleName, null, MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = new DynamicModuleInstaller(a);
				inst.uninstall(i.moduleName).onSuccess(v -> Log.i("Module uninstalled: ", i.moduleName));
			});
		}
	}

	public interface Listener {
		void addonChanged(AddonManager mgr, AddonInfo info, boolean installed);
	}
}
