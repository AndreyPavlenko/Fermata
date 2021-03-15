package me.aap.fermata.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class CarService extends CarActivityService {

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MainCarActivity.class;
	}

	@Override
	public void onCreate() {
		Log.d("Creating CarService: " + this);
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d("Destroying CarService: " + this);
		MainActivityDelegate a = MainCarActivity.delegate;

		if (a != null) {
			FermataServiceUiBinder b = a.getMediaServiceBinder();

			if (b != null) {
				MediaSessionCallback cb = b.getMediaSessionCallback();
				if (cb.isPlaying()) cb.onPause();
			}
		}

		super.onDestroy();
	}
}
