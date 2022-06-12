package me.aap.fermata.addon.felex;

import static me.aap.fermata.util.Utils.getAddonsCacheDir;
import static me.aap.fermata.util.Utils.getAddonsFileDir;
import static me.aap.fermata.util.Utils.isExternalStorageManager;
import static me.aap.fermata.util.Utils.isSafSupported;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.io.File;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.felex.view.FelexFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.VirtualFolder;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class FelexAddon implements FermataAddon {
	private static final AddonInfo info = FermataAddon.findAddonInfo(FelexAddon.class.getName());
	public static final Pref<Supplier<String>> DICT_FOLDER = Pref.s("FELEX_DICT_FOLDER",
			() -> getAddonsFileDir(info).getAbsolutePath());
	public static final Pref<Supplier<String>> CACHE_FOLDER = Pref.s("FELEX_CACHE_FOLDER",
			() -> getAddonsCacheDir(info).getAbsolutePath());

	public static FelexAddon get() {
		return FermataApplication.get().getAddonManager().getAddon(FelexAddon.class);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.felex_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	public FutureSupplier<VirtualFolder> getDictFolder() {
		return getFolder(DICT_FOLDER);
	}

	public FutureSupplier<VirtualFolder> getCacheFolder() {
		return getFolder(CACHE_FOLDER);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private FutureSupplier<VirtualFolder> getFolder(Pref<Supplier<String>> p) {
		String uri = getPreferenceStore().getStringPref(p);
		if (uri.startsWith("/")) {
			if (p.getDefaultValue().get().equals(uri)) new File(uri).mkdirs();
			uri = "file:/" + uri;
		}
		return FermataApplication.get().getVfsManager().getFolder(uri);
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new FelexFragment();
	}

	@Override
	public void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
		contributeFolder(DICT_FOLDER, R.string.dict_folder, set, visibility);
		contributeFolder(CACHE_FOLDER, R.string.cache_folder, set, visibility);
	}

	private void contributeFolder(Pref<Supplier<String>> p, int title,
																PreferenceSet set, ChangeableCondition visibility) {
		set.addFilePref(o -> {
			o.pref = p;
			o.store = getPreferenceStore();
			o.mode = FilePickerFragment.FOLDER | FilePickerFragment.WRITABLE;
			o.title = title;
			o.visibility = visibility;
			o.trim = true;
			o.removeBlank = true;
			o.useSaf = !isExternalStorageManager() && isSafSupported(null);
		});
	}

	private PreferenceStore getPreferenceStore() {
		return FermataApplication.get().getPreferenceStore();
	}
}
