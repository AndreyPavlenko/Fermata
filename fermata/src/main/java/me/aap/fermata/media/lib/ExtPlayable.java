package me.aap.fermata.media.lib;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class ExtPlayable extends PlayableItemBase {

	public ExtPlayable(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
		super(id, parent, resource);
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@Override
	public boolean isExternal() {
		return true;
	}
}
