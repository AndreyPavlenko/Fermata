package me.aap.fermata.ui.activity;

import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.ActivityListener;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityListener extends ActivityListener {
	byte MODE_CHANGED = (byte) (LAST << 1);

	void onActivityEvent(MainActivityDelegate a, long e);

	@Override
	default void onActivityEvent(ActivityDelegate a, long e) {
		onActivityEvent((MainActivityDelegate) a, e);
	}
}
