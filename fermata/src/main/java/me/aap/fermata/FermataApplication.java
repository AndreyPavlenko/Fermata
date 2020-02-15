package me.aap.fermata;

import android.content.SharedPreferences;

import me.aap.utils.app.App;
import me.aap.utils.app.SplitCompatApp;

/**
 * @author Andrey Pavlenko
 */
public class FermataApplication extends SplitCompatApp {
	private SharedPreferences uriToPathMap;
	private SharedPreferences defaultPrefs;

	public static FermataApplication get() {
		return App.get();
	}

	public SharedPreferences getUriToPathMap() {
		if (uriToPathMap == null) uriToPathMap = getSharedPreferences("uri_to_path", MODE_PRIVATE);
		return uriToPathMap;
	}

	public SharedPreferences getDefaultSharedPreferences() {
		if (defaultPrefs == null) defaultPrefs = getSharedPreferences("fermata", MODE_PRIVATE);
		return defaultPrefs;
	}
}
