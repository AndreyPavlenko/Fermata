package me.aap.utils.event;

import java.util.Collection;
import java.util.LinkedList;

/**
 * @author Andrey Pavlenko
 */
public class BasicEventBroadcaster<L> implements EventBroadcaster<L> {
	private final Collection<ListenerRef<L>> listeners = new LinkedList<>();

	@Override
	public Collection<ListenerRef<L>> getBroadcastEventListeners() {
		return listeners;
	}
}
