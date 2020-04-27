package me.aap.fermata.vfs;

import android.content.Context;

import java.util.List;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;

/**
 * @author Andrey Pavlenko
 */
public interface VfsProvider {

	FutureSupplier<VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier,
			PreferenceStore ps);

	FutureSupplier<VirtualFolder> select(MainActivityDelegate a, List<VirtualFileSystem> fs);
}
