package me.aap.fermata.ui.activity;

/**
 * @author Andrey Pavlenko
 */
public interface MainActivityListener {
	enum Event {
		SERVICE_BOUND,
		BACK_PRESSED,
		FRAGMENT_CHANGED,
		FRAGMENT_CONTENT_CHANGED,
		FILTER_CHANGED,
		ACTIVITY_FINISH;

		public long mask() {
			return 1L << ordinal();
		}
	}

	static long mask(Event... events) {
		long mask = 0;
		for (Event e : events) {
			mask |= e.mask();
		}
		return mask;
	}

	void onMainActivityEvent(MainActivityDelegate a, Event e);

	default boolean handleActivityFinishEvent(MainActivityDelegate a, Event e) {
		if (e == Event.ACTIVITY_FINISH) {
			a.removeBroadcastListener(this);
			return true;
		} else {
			return false;
		}
	}
}
