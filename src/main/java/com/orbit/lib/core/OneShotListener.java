package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An {@link EventListener} wrapper that fires the delegate exactly once,
 * then removes itself from the bus.
 *
 * <p>Thread safety is guaranteed by {@link AtomicBoolean#compareAndSet}: even
 * if multiple threads publish the same event simultaneously, the delegate is
 * called by at most one of them.
 *
 * @param <T> the event type
 */
final class OneShotListener<T extends Event> implements EventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(OneShotListener.class);

    private final AtomicBoolean fired = new AtomicBoolean(false);
    private final Class<T> eventType;
    private final EventListener<T> delegate;
    private final EventBus bus;

    OneShotListener(Class<T> eventType, EventListener<T> delegate, EventBus bus) {
        this.eventType = eventType;
        this.delegate = delegate;
        this.bus = bus;
    }

    @Override
    public void onEvent(T event) {
        if (fired.compareAndSet(false, true)) {
            LOG.debug("OneShotListener for [{}] fired, self-removing", eventType.getSimpleName());
            try {
                delegate.onEvent(event);
            } finally {
                bus.unsubscribe(eventType, this);
            }
        }
    }
}
