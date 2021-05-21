package me.aap.fermata.addon.tv;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class TvAddon implements MediaLibAddon {
	private static TvRootItem root;

	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.tv_fragment;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new TvFragment();
	}


	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id) {
		return getRootItem(lib).getItem(scheme, id);
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return (i instanceof TvItem);
	}

	public static TvRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) root = new TvRootItem(lib);
		return root;
	}
}
