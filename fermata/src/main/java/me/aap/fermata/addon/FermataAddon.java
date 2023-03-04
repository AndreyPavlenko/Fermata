package me.aap.fermata.addon;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.media.engine.MediaEngineProvider;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.io.FileUtils;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public interface FermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	AddonInfo getInfo();

	@NonNull
	ActivityFragment createFragment();

	default int getFragmentId() {
		return getAddonId();
	}

	default void contributeSettings(PreferenceStore store, PreferenceSet set, ChangeableCondition visibility) {
	}

	default void install() {
	}

	default void uninstall() {
	}

	default boolean handleIntent(MainActivityDelegate a, Intent intent) {
		return false;
	}

	@Nullable
	default ParcelFileDescriptor openFile(Uri uri) throws FileNotFoundException {
		String s = uri.getScheme();
		if (s == null) throw new FileNotFoundException(uri.toString());
		if (s.equals("file")) {
			return ParcelFileDescriptor.open(new File(uri.toString().substring(6)), MODE_READ_ONLY);
		} else if (s.equals("content")) {
			return App.get().getContentResolver().openFileDescriptor(uri, "r");
		}
		throw new FileNotFoundException(uri.toString());
	}

	@Nullable
	default String getFileType(Uri uri, String displayName) {
		if (displayName == null) displayName = uri.getPath();
		return FileUtils.getMimeType(displayName);
	}

	@Nullable
	default MediaEngineProvider getMediaEngineProvider(int id) {
		return null;
	}

	@NonNull
	static AddonInfo findAddonInfo(String name) {
		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (ai.className.equals(name)) return ai;
		}
		throw new RuntimeException("Addon not found: " + name);
	}
}
