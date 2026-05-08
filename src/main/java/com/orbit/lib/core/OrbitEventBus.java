package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventListener;

import java.util.Objects;

/**
 * Thread-safe implementation of {@link EventBus}.
 *
 * <p>Delegates listener storage and retrieval to {@link ListenerRegistry}.
 */
public final class OrbitEventBus implements EventBus {

    private final ListenerRegistry registry = new ListenerRegistry();
    private final Dispatcher dispatcher = new Dispatcher();

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, listener);
        return this;
    }

    @Override
    public <T extends Event> EventBus publish(T event) {
        Objects.requireNonNull(event, "event must not be null");

        dispatcher.dispatch(event, registry.getListeners(event.getClass()));
        return this;
    }

    @Override
    public int listenerCount(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return registry.count(eventType);
    }
}
