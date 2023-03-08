package me.aap.fermata.addon;

import static java.util.Collections.singletonList;

import android.app.Activity;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
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
	public FermataAddon getAddon(String moduleOrClassName) {
		if (moduleOrClassName.indexOf('.') < 0) {
			for (FermataAddon a : addons.values()) {
				if (a.getInfo().getModuleName().equals(moduleOrClassName)) return a;
			}
		} else {
			return addons.get(moduleOrClassName);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) addons.get(c.getName());
	}

	public Collection<FermataAddon> getAddons() {
		return addons.values();
	}

	public boolean hasAddon(@IdRes int id) {
		for (FermataAddon a : getAddons()) {
			if (a.getAddonId() == id) return true;
		}
		return false;
	}

	@Nullable
	public ActivityFragment createFragment(@IdRes int id) {
		for (FermataAddon a : getAddons()) {
			if (a instanceof FermataFragmentAddon) {
				if (a.getAddonId() == id) return ((FermataFragmentAddon) a).createFragment();
			}
		}
		return null;
	}

	@Nullable
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		for (FermataAddon a : getAddons()) {
			if (a instanceof MediaLibAddon) {
				FutureSupplier<? extends Item> i = ((MediaLibAddon) a).getItem(lib, scheme, id);
				if (i != null) return i;
			}
		}

		return null;
	}

	@Nullable
	public MediaLibAddon getMediaLibAddon(Item i) {
		for (FermataAddon a : getAddons()) {
			if (a instanceof MediaLibAddon) {
				MediaLibAddon mla = (MediaLibAddon) a;
				if (mla.isSupportedItem(i)) return mla;
			}
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
				PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
				a.install();
				addons.put(i.className, a);
				fireBroadcastEvent(c -> c.onAddonChanged(this, i, true));
				prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));
				return;
			} catch (Exception ignore) {
			}

			if (!installModule) return;

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon,
					i.moduleName, null, MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = createInstaller(a, i);
				inst.install(i.moduleName).onSuccess(v -> {
					Log.i("Module installed: ", i.moduleName);

					for (AddonInfo ai : BuildConfig.ADDONS) {
						if (i.moduleName.equals(ai.moduleName)) install(ai, false);
					}
				});
			});
		}
	}

	private void uninstall(AddonInfo i) {
		FermataAddon removed = addons.remove(i.className);

		if (removed != null) {
			removed.uninstall();
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			fireBroadcastEvent(c -> c.onAddonChanged(this, i, false));
			prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));

			for (AddonInfo ai : BuildConfig.ADDONS) {
				if (ai.moduleName.equals(i.moduleName)) return;
			}

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon,
					i.moduleName, null, MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = createInstaller(a, i);
				inst.uninstall(i.moduleName).onSuccess(v -> Log.i("Module uninstalled: ", i.moduleName));
			});
		}
	}

	private static DynamicModuleInstaller createInstaller(Activity a, AddonInfo ai) {
		DynamicModuleInstaller i = new DynamicModuleInstaller(a);
		String name = a.getString(ai.addonName);
		i.setSmallIcon(R.drawable.notification);
		i.setTitle(a.getString(R.string.module_installation, name));
		i.setNotificationChannel(CHANNEL_ID, a.getString(R.string.installing, name));
		i.setPendingMessage(a.getString(R.string.install_pending, name));
		i.setDownloadingMessage(a.getString(R.string.downloading, name));
		i.setInstallingMessage(a.getString(R.string.installing, name));
		return i;
	}

	public interface Listener {
		void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed);
	}
}
