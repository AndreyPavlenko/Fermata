package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.utils.async.Completed.completedEmptyList;

/**
 * @author Andrey Pavlenko
 */
public abstract class ExtBrowsable extends BrowsableItemBase {

	public ExtBrowsable(String id, @Nullable BrowsableItem parent, @Nullable VirtualResource resource) {
		super(id, parent, resource);
	}

	@Override
	protected FutureSupplier<List<MediaLib.Item>> listChildren() {
		return completedEmptyList();
	}

	@Override
	public boolean isExternal() {
		return true;
	}
}
