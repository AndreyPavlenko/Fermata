package me.aap.fermata.media.lib;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;

import static me.aap.utils.async.Completed.completedEmptyList;

/**
 * @author Andrey Pavlenko
 */
public class ExtRoot extends ExtBrowsable {
	private final MediaLib lib;

	public ExtRoot(String id, MediaLib lib) {
		super(id, null, null);
		this.lib = lib;
	}

	@NonNull
	@Override
	public MediaLib getLib() {
		return lib;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		MediaLib lib = getLib();
		if (lib instanceof PreferenceStore) return (PreferenceStore) lib;
		throw new UnsupportedOperationException();
	}

	@NonNull
	@Override
	public MediaLib.BrowsableItem getRoot() {
		return this;
	}

	@Override
	public boolean isExternal() {
		return true;
	}
}
