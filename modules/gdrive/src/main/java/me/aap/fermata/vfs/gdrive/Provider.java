package me.aap.fermata.vfs.gdrive;

import android.content.Context;

import me.aap.fermata.vfs.VfsProvider;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.gdrive.GdriveFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class Provider implements VfsProvider {

	@Override
	public VirtualFileSystem.Provider getProvider(Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier) {
		return GdriveFileSystem.Provider.create(ctx.getString(me.aap.fermata.R.string.google_app_id),
				activitySupplier);
	}
}
