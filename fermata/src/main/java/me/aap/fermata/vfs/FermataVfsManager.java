package me.aap.fermata.vfs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.vfs.VfsException;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.content.ContentFileSystem;
import me.aap.utils.vfs.local.LocalFileSystem;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class FermataVfsManager extends VfsManager {
	private static final String CHANNEL_ID = "fermata.vfs.install";
	private static final PreferenceStore.Pref<BooleanSupplier> PREFER_FILE_API = PreferenceStore.Pref.b("PREFER_FILE_API", true);

	private static final PreferenceStore.Pref<BooleanSupplier> ENABLE_GDRIVE = PreferenceStore.Pref.b("ENABLE_GDRIVE", false);
	private static final String GDRIVE_ID = "gdrive";
	private static final String GDRIVE_CLASS = "me.aap.fermata.vfs.gdrive.Provider";

	public FermataVfsManager() {
		super(providers());

		PreferenceStore ps = FermataApplication.get().getPreferenceStore();

		if (ps.getBooleanPref(ENABLE_GDRIVE)) getGdriveProvider();
	}

	public FutureSupplier<VirtualFileSystem.Provider> getGdriveProvider() {
		List<VirtualFileSystem.Provider> p = getProviders("gdrive");
		if (!p.isEmpty()) return completed(p.get(0));
		FermataApplication.get().getPreferenceStore().applyBooleanPref(ENABLE_GDRIVE, true);
		return installModule(GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive);
	}

	private static FutureSupplier<MainActivity> getActivity(@StringRes int moduleName) {
		Context ctx = App.get();
		String name = ctx.getString(moduleName);
		String title = ctx.getString(R.string.module_installation, name);
		return ActivityBase.create(ctx, CHANNEL_ID, title, R.drawable.ic_notification,
				title, null, MainActivity.class);
	}

	private static List<VirtualFileSystem.Provider> providers() {
		Context ctx = App.get();
		FermataApplication app = FermataApplication.get();
		PreferenceStore ps = app.getPreferenceStore();
		List<VirtualFileSystem.Provider> p = new ArrayList<>(3);
		p.add(LocalFileSystem.Provider.getInstance());
		p.add(new ContentFileSystem.Provider(ps.getBooleanPref(PREFER_FILE_API)));
		addProvider(p, ps, ENABLE_GDRIVE, ctx, GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive);
		return p;
	}

	private static void addProvider(
			List<VirtualFileSystem.Provider> providers, PreferenceStore ps,
			PreferenceStore.Pref<BooleanSupplier> p, Context ctx, String className,
			String moduleId, @StringRes int moduleName) {
		if (!ps.getBooleanPref(p)) return;

		VirtualFileSystem.Provider provider = loadModule(ctx, className, moduleId, moduleName);
		if (provider != null) providers.add(provider);
	}

	private static VirtualFileSystem.Provider loadModule(Context ctx, String className, String moduleId,
																											 @StringRes int moduleName) {
		try {
			VfsProvider p = (VfsProvider) Class.forName(className).newInstance();
			return p.getProvider(ctx, () -> getActivity(moduleName));
		} catch (Throwable ex) {
			Log.e(FermataVfsManager.class.getName(), "Failed to load module " + moduleId);
			return null;
		}
	}

	private FutureSupplier<VirtualFileSystem.Provider> installModule(
			String className, String moduleId, @StringRes int moduleName) {
		return getActivity(moduleName).then(a -> {
			String name = a.getString(moduleName);
			String title = a.getString(R.string.module_installation, name);
			DynamicModuleInstaller i = new DynamicModuleInstaller(a);
			i.setSmallIcon(R.drawable.ic_notification);
			i.setTitle(a.getString(R.string.install_pending, name));
			i.setNotificationChannel(CHANNEL_ID, title);
			i.setPendingMessage(a.getString(R.string.install_pending, name));
			i.setDownloadingMessage(a.getString(R.string.downloading, name));
			i.setInstallingMessage(a.getString(R.string.installing, name));

			return i.install(moduleId).then(v -> {
				VirtualFileSystem.Provider provider = loadModule(a, className, moduleId, moduleName);

				if (provider != null) {
					addProviders(provider);
					return completed(provider);
				} else {
					return failed(new VfsException("Failed to install module " + moduleId));
				}
			});
		});
	}
}
