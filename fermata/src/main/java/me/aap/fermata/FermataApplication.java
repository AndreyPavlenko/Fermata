package me.aap.fermata;

import android.content.SharedPreferences;
import android.os.Handler;

import com.google.android.play.core.splitcompat.SplitCompatApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Andrey Pavlenko
 */
public class FermataApplication extends SplitCompatApplication {
	private static FermataApplication app;
	private Handler handler;
	private ExecutorService executor;
	private SharedPreferences uriToPathMap;
	private SharedPreferences defaultPrefs;

	public static FermataApplication get() {
		return app;
	}

	public Handler getHandler() {
		return handler;
	}

	public ExecutorService getExecutor() {
		return executor;
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
	public final void onCreate() {
		super.onCreate();
		app = this;
		handler = new Handler();
		executor = Executors.newSingleThreadExecutor();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		if (executor != null) executor.shutdown();
	}
}
