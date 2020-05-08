package me.aap.fermata.vfs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.ActivityBase;
import me.aap.utils.vfs.VfsException;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.content.ContentFileSystem;
import me.aap.utils.vfs.generic.GenericFileSystem;
import me.aap.utils.vfs.local.LocalFileSystem;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class FermataVfsManager extends VfsManager {
	public static final String GDRIVE_ID = "gdrive";
	public static final String SFTP_ID = "sftp";
	private static final String CHANNEL_ID = "fermata.vfs.install";
	private static final Pref<BooleanSupplier> ENABLE_GDRIVE = Pref.b("ENABLE_GDRIVE", false);
	private static final Pref<BooleanSupplier> ENABLE_SFTP = Pref.b("ENABLE_SFTP", false);
	private static final String GDRIVE_CLASS = "me.aap.fermata.vfs.gdrive.Provider";
	private static final String SFTP_CLASS = "me.aap.fermata.vfs.sftp.Provider";

	public FermataVfsManager() {
		super(filesystems());

		PreferenceStore ps = FermataApplication.get().getPreferenceStore();
		initProvider(ps, ENABLE_GDRIVE, GDRIVE_ID);
		initProvider(ps, ENABLE_SFTP, SFTP_ID);
	}

	public FutureSupplier<VfsProvider> getProvider(String scheme) {
		switch (scheme) {
			case GDRIVE_ID:
				return getProvider(scheme, ENABLE_GDRIVE, GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive);
			case SFTP_ID:
				return getProvider(scheme, ENABLE_SFTP, SFTP_CLASS, SFTP_ID, R.string.vfs_sftp);
			default:
				return completedNull();
		}
	}

	private static FutureSupplier<MainActivity> getActivity(Context ctx, @StringRes int moduleName) {
		String name = ctx.getString(moduleName);
		String title = ctx.getString(R.string.module_installation, name);
		return ActivityBase.create(ctx, CHANNEL_ID, title, R.drawable.ic_notification,
				title, null, MainActivity.class);
	}

	private static List<VirtualFileSystem> filesystems() {
		FermataApplication app = FermataApplication.get();
		PreferenceStore ps = app.getPreferenceStore();
		List<VirtualFileSystem> p = new ArrayList<>(5);
		p.add(LocalFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		p.add(GenericFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		p.add(ContentFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		addFileSystem(p, ps, ENABLE_GDRIVE, app, GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive);
		addFileSystem(p, ps, ENABLE_SFTP, app, SFTP_CLASS, SFTP_ID, R.string.vfs_sftp);
		return p;
	}

	private static void addFileSystem(
			List<VirtualFileSystem> fileSystems, PreferenceStore ps,
			Pref<BooleanSupplier> p, Context ctx, String className,
			String moduleId, @StringRes int moduleName) {
		if (!ps.getBooleanPref(p)) return;
		VfsProvider provider = loadProvider(className, moduleId);

		if (provider != null) {
			FutureSupplier<VirtualFileSystem> f = provider
					.createFileSystem(ctx, () -> getActivity(ctx, moduleName), ps);
			if (f.isDone() && !f.isFailed()) fileSystems.add(f.getOrThrow());
		}
	}

	private static VfsProvider loadProvider(String className, String moduleId) {
		try {
			return (VfsProvider) Class.forName(className).newInstance();
		} catch (Throwable ex) {
			Log.e(FermataVfsManager.class.getName(), "Failed to load module " + moduleId);
			return null;
		}
	}

	private void initProvider(PreferenceStore ps, Pref<BooleanSupplier> p, String scheme) {
		if (!ps.getBooleanPref(p) || isSupportedScheme(scheme)) return;
		getProvider(scheme).onFailure(fail -> Log.e(getClass().getName(),
				"Failed to initiate provider " + scheme, fail));
	}

	private FutureSupplier<VfsProvider> getProvider(
			String scheme, Pref<BooleanSupplier> pref,
			String className, String moduleId, @StringRes int moduleName) {
		VfsProvider p = loadProvider(className, moduleId);

		if (p != null) {
			if (isSupportedScheme(scheme)) return completed(p);
			FermataApplication.get().getPreferenceStore().applyBooleanPref(pref, true);
			return addProvider(p, moduleName).map(fs -> p);
		} else {
			return installModule(className, moduleId, moduleName, pref)
					.then(provider -> addProvider(provider, moduleName).map(fs -> provider));
		}
	}

	private FutureSupplier<VirtualFileSystem> addProvider(VfsProvider p, @StringRes int moduleName) {
		FermataApplication app = FermataApplication.get();
		return p.createFileSystem(app, () -> getActivity(app, moduleName), app.getPreferenceStore())
				.onSuccess(this::mount);
	}

	private FutureSupplier<VfsProvider> installModule(
			String className, String moduleId, @StringRes int moduleName, Pref<BooleanSupplier> pref) {
		FermataApplication app = FermataApplication.get();
		return getActivity(app, moduleName).then(a -> {
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
				VfsProvider p = loadProvider(className, moduleId);

				if (p != null) {
					app.getPreferenceStore().applyBooleanPref(pref, true);
					return completed(p);
				} else {
					return failed(new VfsException("Failed to install module " + moduleId));
				}
			});
		});
	}
}
