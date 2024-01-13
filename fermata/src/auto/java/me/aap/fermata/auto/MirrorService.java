package me.aap.fermata.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

/**
 * @author Andrey Pavlenko
 */
public class MirrorService extends CarActivityService {
	static MirrorDisplay md;

	@Override
	public void onCreate() {
		super.onCreate();
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		md.release();
		md = null;
		super.onDestroy();
	}

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MirrorActivity.class;
	}
}
