package me.aap.fermata.addon.felex;

import static me.aap.fermata.addon.felex.dict.DictMgr.DICT_EXT;
import static me.aap.fermata.util.Utils.getAddonsCacheDir;
import static me.aap.fermata.util.Utils.getAddonsFileDir;
import static me.aap.fermata.util.Utils.isExternalStorageManager;
import static me.aap.fermata.util.Utils.isSafSupported;
import static me.aap.utils.io.FileUtils.getFileExtension;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataContentAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.addon.felex.dict.DictInfo;
import me.aap.fermata.addon.felex.view.FelexFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
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
public class FelexAddon implements FermataFragmentAddon, FermataContentAddon {
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

	@Override
	public boolean handleIntent(MainActivityDelegate a, Intent intent) {
		Uri u = intent.getData();
		if (u == null) return false;

		String s = u.getScheme();
		if ((s == null) || (!s.equals("file") && !s.equals("content"))) return false;

		String ext = FileUtils.getFileExtension(u.getPath());

		if ((ext != null) && DICT_EXT.regionMatches(1, ext, 0, ext.length())) {
			a.showFragment(getFragmentId(), u);
			return true;
		}

		try (InputStream in = a.getContext().getContentResolver().openInputStream(u)) {
			if (DictInfo.read(in) == null) return false;
		} catch (IOException ex) {
			Log.d(ex, "Failed to read uri: ", u);
			return false;
		}

		a.showFragment(getFragmentId(), u);
		return true;
	}

	@Nullable
	@Override
	public String getFileType(Uri uri, String displayName) {
		if (displayName == null) displayName = uri.getPath();
		String ext = getFileExtension(displayName);
		if ((ext != null) && DICT_EXT.regionMatches(1, ext, 0, ext.length())) return "text/plain";
		return FermataContentAddon.super.getFileType(uri, displayName);

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
