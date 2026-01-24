package me.aap.utils.ui.activity;

/**
 * @author Andrey Pavlenko
 */
public interface ActivityListener {
	byte SERVICE_BOUND = 1;
	byte FRAGMENT_CHANGED = (byte) (1 << 1);
	byte FRAGMENT_CONTENT_CHANGED = (byte) (1 << 2);
	byte ACTIVITY_FINISH = (byte) (1 << 3);
	byte ACTIVITY_DESTROY = (byte) (1 << 4);
	byte LAST = ACTIVITY_DESTROY;

	void onActivityEvent(ActivityDelegate a, long e);

	default boolean handleActivityDestroyEvent(ActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) {
			a.removeBroadcastListener(this);
			return true;
		} else {
			return false;
		}
	}
}
