package me.aap.fermata.addon;

import static java.util.Collections.singletonList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.app.Activity;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;
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
	private final Map<Object, FermataAddon> map = new HashMap<>();
	private final List<FermataAddon> addons = new ArrayList<>(BuildConfig.ADDONS.length);
	private final Map<String, FutureSupplier<?>> installing = new HashMap<>();

	public AddonManager(PreferenceStore store) {
		for (AddonInfo i : BuildConfig.ADDONS) {
			if (!store.getBooleanPref(i.enabledPref)) continue;
			try {
				FermataAddon a = (FermataAddon) Class.forName(i.className).newInstance();
				add(a);
			} catch (Exception ignore) {
			}
		}

		store.addBroadcastListener(this);
	}

	public static AddonManager get() {
		return FermataApplication.get().getAddonManager();
	}

	@Nullable
	public synchronized FermataAddon getAddon(String moduleOrClassName) {
		return map.get(moduleOrClassName);
	}

	public <A extends FermataAddon> FutureSupplier<A> getOrInstallAddon(Class<A> c) {
		return getOrInstallAddon(c.getName()).cast();
	}

	public synchronized FutureSupplier<FermataAddon> getOrInstallAddon(String moduleOrClassName) {
		var a = getAddon(moduleOrClassName);
		if (a != null) return completed(a);
		var info = FermataAddon.findAddonInfo(moduleOrClassName);
		var prefs = FermataApplication.get().getPreferenceStore();
		if (!prefs.getBooleanPref(info.enabledPref)) prefs.applyBooleanPref(info.enabledPref, true);
		install(info);
		var pending = installing.get(info.className);
		if (pending == null) {
			a = getAddon(moduleOrClassName);
			return a != null ? completed(a) :
					failed(new RuntimeException("Failed to install addon: " + moduleOrClassName));
		}
		return pending.then(v -> getOrInstallAddon(info.className));
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public synchronized <A extends FermataAddon> A getAddon(Class<A> c) {
		return (A) map.get(c.getName());
	}

	public synchronized Collection<FermataAddon> getAddons() {
		return new ArrayList<>(addons);
	}

	/**
	 * @noinspection unchecked
	 */
	public synchronized <A extends FermataAddon> List<A> getAddons(Class<A> c) {
		return (List<A>) CollectionUtils.filter(addons, c::isInstance);
	}

	public synchronized boolean hasAddon(@IdRes int id) {
		return map.containsKey(id);
	}

	@Nullable
	public synchronized ActivityFragment createFragment(@IdRes int id) {
		FermataAddon a = map.get(id);
		if (a instanceof FermataFragmentAddon fa) return fa.createFragment();
		return null;
	}

	@Nullable
	public synchronized FutureSupplier<? extends Item>
	getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		for (FermataAddon a : addons) {
			if (a instanceof MediaLibAddon) {
				FutureSupplier<? extends Item> i = ((MediaLibAddon) a).getItem(lib, scheme, id);
				if (i != null) return i;
			}
		}

		return null;
	}

	@Nullable
	public synchronized MediaLibAddon getMediaLibAddon(Item i) {
		for (FermataAddon a : addons) {
			if (a instanceof MediaLibAddon mla) {
				if (mla.isSupportedItem(i)) return mla;
			}
		}

		return null;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		for (AddonInfo i : BuildConfig.ADDONS) {
			if (prefs.contains(i.enabledPref)) {
				if (store.getBooleanPref(i.enabledPref)) install(i);
				else uninstall(i);
			}
		}
	}

	private synchronized void install(AddonInfo i) {
		if (loadAddon(i) || installing.containsKey(i.className)) return;
		for (String dep : i.depends) install(FermataAddon.findAddonInfo(dep));

		var task = ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon, i.moduleName, null,
				MainActivity.class).then(a -> createInstaller(a, i).install(i.moduleName)).onSuccess(v -> {
			Log.i("Module installed: ", i.moduleName);
			if (loadAddon(i)) return;
			Log.i("Failed to load addon, retrying: ", i.className);
			var p = new Promise<Void>();
			installing.put(i.className, p);
			p.thenRun(() -> installCompleted(i, p));
			scheduleLoadAddon(i, p, 1);
		}).onFailure(err -> {
			if (!isCancellation(err)) Log.e(err, "Failed to install module: ", i.moduleName);
		});
		installing.put(i.className, task);
		task.thenRun(() -> installCompleted(i, task));
		scheduleLoadAddon(i, task, 1);
	}

	private synchronized void installCompleted(AddonInfo i, FutureSupplier<Void> task) {
		CollectionUtils.remove(installing, i.className, task);
	}

	private synchronized boolean loadAddon(AddonInfo i) {
		if (map.containsKey(i.className)) return true;
		try {
			FermataAddon a = (FermataAddon) Class.forName(i.className).newInstance();
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			a.install();
			add(a);
			fireBroadcastEvent(c -> c.onAddonChanged(this, i, true));
			prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));
			Log.i("Addon loaded: ", i.className);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}

	private synchronized void scheduleLoadAddon(AddonInfo i, FutureSupplier<?> task, int counter) {
		App.get().getHandler().postDelayed(() -> {
			if (installing.get(i.className) != task) return;
			if (loadAddon(i)) {
				task.cancel();
			} else if (counter == 180) {
				Log.e("Failed load addon in 180 seconds: ", i.className);
				task.cancel();
			} else {
				scheduleLoadAddon(i, task, counter + 1);
			}
		}, 1000);
	}

	private synchronized void uninstall(AddonInfo i) {
		var task = installing.get(i.className);
		if (task != null) task.cancel();
		var removed = remove(i);

		if (removed != null) {
			removed.uninstall();
			PreferenceStore prefs = FermataApplication.get().getPreferenceStore();
			fireBroadcastEvent(c -> c.onAddonChanged(this, i, false));
			prefs.fireBroadcastEvent(l -> l.onPreferenceChanged(prefs, singletonList(i.enabledPref)));

			for (AddonInfo ai : BuildConfig.ADDONS) {
				if (ai.moduleName.equals(i.moduleName)) return;
			}

			ActivityBase.create(App.get(), CHANNEL_ID, i.moduleName, i.icon, i.moduleName, null,
					MainActivity.class).onSuccess(a -> {
				DynamicModuleInstaller inst = createInstaller(a, i);
				inst.uninstall(i.moduleName).onSuccess(v -> Log.i("Module uninstalled: ", i.moduleName));
			});
		}
	}

	private synchronized void add(FermataAddon a) {
		var info = a.getInfo();
		addons.add(a);
		map.put(a.getAddonId(), a);
		map.put(info.className, a);
		map.put(info.moduleName, a);
	}

	private synchronized FermataAddon remove(AddonInfo info) {
		FermataAddon a = map.remove(info.className);
		if (a == null) return null;
		addons.remove(a);
		map.remove(a.getAddonId());
		map.remove(info.moduleName);
		return a;
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
