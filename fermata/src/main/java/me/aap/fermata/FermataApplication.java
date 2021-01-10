package me.aap.fermata;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.io.File;

import me.aap.fermata.addon.AddonManager;
import me.aap.utils.app.App;
import me.aap.utils.app.NetSplitCompatApp;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class FermataApplication extends NetSplitCompatApp {
	private volatile SharedPreferenceStore preferenceStore;
	private volatile AddonManager addonManager;

	public static FermataApplication get() {
		return App.get();
	}

	public PreferenceStore getPreferenceStore() {
		SharedPreferenceStore ps = preferenceStore;

		if (ps == null) {
			synchronized (this) {
				if ((ps = preferenceStore) == null) {
					preferenceStore = ps = SharedPreferenceStore.create(getSharedPreferences("fermata", MODE_PRIVATE));
				}
			}
		}

		return ps;
	}

	public SharedPreferences getDefaultSharedPreferences() {
		return ((SharedPreferenceStore) getPreferenceStore()).getSharedPreferences();
	}

	public AddonManager getAddonManager() {
		AddonManager mgr = addonManager;

		if (mgr == null) {
			synchronized (this) {
				if ((mgr = addonManager) == null) {
					addonManager = mgr = new AddonManager(getPreferenceStore());
				}
			}
		}

		return mgr;
	}

	@Override
	protected int getMaxNumberOfThreads() {
		return 5;
	}

	@Nullable
	@Override
	public File getLogFile() {
		return BuildConfig.AUTO ? new File(getFilesDir(), "Fermata.log") : null;
	}
}
