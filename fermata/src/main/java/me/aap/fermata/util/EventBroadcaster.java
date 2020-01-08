package me.aap.fermata.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import me.aap.fermata.FermataApplication;

/**
 * @author Andrey Pavlenko
 */
public interface EventBroadcaster<L> {
	long ALL_EVENTS_MASK = 0xFFFFFFFFFFFFFFFFL;

	Collection<ListenerRef<L>> getBroadcastEventListeners();

	default void addBroadcastListener(L listener) {
		addBroadcastListener(listener, ALL_EVENTS_MASK);
	}

	default void addBroadcastListener(L listener, long eventMask) {
		boolean add = true;
		Collection<ListenerRef<L>> listeners = getBroadcastEventListeners();

		for (Iterator<ListenerRef<L>> it = listeners.iterator(); it.hasNext(); ) {
			L l = it.next().get();
			if (l == null) it.remove();
			else if (l == listener) add = false;
		}

		if (add) listeners.add(new ListenerRef<>(listener, eventMask));
	}

	default void removeBroadcastListener(L listener) {
		for (Iterator<ListenerRef<L>> it = getBroadcastEventListeners().iterator(); it.hasNext(); ) {
			L l = it.next().get();
			if ((l == null) || (l == listener)) it.remove();
		}
	}

	default void postBroadcastEvent(Consumer<L> broadcaster, long eventMask) {
		FermataApplication.get().getHandler().post(() -> fireBroadcastEvent(broadcaster, eventMask));
	}

	default void fireBroadcastEvent(Consumer<L> broadcaster) {
		fireBroadcastEvent(broadcaster, ALL_EVENTS_MASK);
	}

	default void fireBroadcastEvent(Consumer<L> broadcaster, long eventMask) {
		Collection<ListenerRef<L>> listeners = getBroadcastEventListeners();
		List<L> list = new ArrayList<>(listeners.size());

		for (Iterator<ListenerRef<L>> it = getBroadcastEventListeners().iterator(); it.hasNext(); ) {
			ListenerRef<L> r = it.next();
			L l = r.get();
			if (l == null) it.remove();
			else if ((r.mask & eventMask) != 0) list.add(l);
		}

		list.forEach(broadcaster);
	}

	class ListenerRef<L> extends WeakReference<L> {
		private final long mask;

		public ListenerRef(L listener, long eventMask) {
			super(listener);
			mask = eventMask;
		}
	}
}
