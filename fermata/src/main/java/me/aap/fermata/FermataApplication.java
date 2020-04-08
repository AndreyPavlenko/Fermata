package me.aap.fermata;

import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;

import me.aap.utils.app.App;
import me.aap.utils.app.SplitCompatApp;
import me.aap.utils.concurrent.PriorityThreadPool;

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

	@Override
	public PriorityThreadPool getExecutor() {
		return (PriorityThreadPool) super.getExecutor();
	}

	@Override
	protected ExecutorService createExecutor() {
		PriorityThreadPool executor = new PriorityThreadPool(2);
		executor.setThreadFactory(this);
		executor.allowCoreThreadTimeOut(true);
		return executor;
	}
}
