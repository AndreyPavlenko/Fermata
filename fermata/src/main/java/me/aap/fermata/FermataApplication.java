package me.aap.fermata;

import android.content.SharedPreferences;

import me.aap.utils.app.App;
import me.aap.utils.app.SplitCompatApp;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class FermataApplication extends SplitCompatApp {
	private volatile SharedPreferenceStore preferenceStore;

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

	@Override
	protected int getMaxNumberOfThreads() {
		return 2;
	}
}
