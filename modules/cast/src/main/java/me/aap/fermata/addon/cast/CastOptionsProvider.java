package me.aap.fermata.addon.cast;

import static com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

import android.content.Context;

import com.google.android.gms.cast.LaunchOptions;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;

import java.util.List;

import me.aap.fermata.ui.activity.FermataActivity;

/**
 * @author Andrey Pavlenko
 */
public class CastOptionsProvider implements OptionsProvider {
	@Override
	public CastOptions getCastOptions(Context context) {
		CastMediaOptions mediaOpts =
				new CastMediaOptions.Builder().setExpandedControllerActivityClassName(
						FermataActivity.class.getName()).build();
		LaunchOptions launchOpts =
				new LaunchOptions.Builder().setAndroidReceiverCompatible(true).build();
		return new CastOptions.Builder().setLaunchOptions(launchOpts)
				.setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
				.setCastMediaOptions(mediaOpts).build();
	}

	@Override
	public List<SessionProvider> getAdditionalSessionProviders(Context appContext) {
		return null;
	}
}