package me.aap.fermata.vfs;

import android.content.Context;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public interface VfsProvider {
	VirtualFileSystem.Provider getProvider(Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier);
}
