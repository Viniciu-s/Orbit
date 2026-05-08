package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventListener;
import com.orbit.lib.api.Priority;

/**
 * Internal wrapper that pairs an {@link EventListener} with a {@link Priority}.
 *
 * <p>Used by {@link ListenerRegistry} to maintain priority-ordered dispatch.
 * This class is an implementation detail and must not be exposed in the public API.
 *
 * <p>Identity-based {@code equals}/{@code hashCode} (inherited from {@link Object})
 * are intentional: each registration produces a unique wrapper instance, allowing
 * {@link ListenerRegistry#deregister} to locate and remove exactly the right entry.
 *
 * @param <T> the event type
 */
final class PrioritizedListener<T extends Event> implements EventListener<T> {

    private final Priority priority;
    private final EventListener<T> delegate;

    PrioritizedListener(Priority priority, EventListener<T> delegate) {
        this.priority = priority;
        this.delegate = delegate;
    }

    Priority priority() {
        return priority;
    }

    EventListener<T> delegate() {
        return delegate;
    }

    @Override
    public void onEvent(T event) {
        delegate.onEvent(event);
    }
}
