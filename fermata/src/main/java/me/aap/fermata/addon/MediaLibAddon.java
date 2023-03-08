package me.aap.fermata.addon;

import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibAddon extends FermataFragmentAddon {

	@Nullable
	FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id);

	boolean isSupportedItem(Item i);
}
