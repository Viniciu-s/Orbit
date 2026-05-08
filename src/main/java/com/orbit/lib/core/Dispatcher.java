package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Internal dispatcher responsible for routing a published event to its listeners.
 *
 * <p>Each listener is invoked inside an isolated try-catch block. A failing
 * listener is logged via SLF4J and skipped — it never interrupts the remaining
 * listeners or propagates an exception to the caller.
 *
 * <p>This class is an implementation detail of {@code OrbitEventBus} and is
 * intentionally package-private.
 */
final class Dispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    /**
     * Dispatches {@code event} to every listener in {@code listeners}.
     *
     * @param <T>       the event type
     * @param event     the event to dispatch; must not be {@code null}
     * @param listeners snapshot of listeners to invoke; must not be {@code null}
     */
    @SuppressWarnings("unchecked")
    <T extends Event> void dispatch(T event, List<EventListener<?>> listeners) {
        for (EventListener<?> listener : listeners) {
            try {
                ((EventListener<T>) listener).onEvent(event);
            } catch (Exception e) {
                LOG.error(
                        "Listener [{}] failed while handling event [{}]: {}",
                        listener.getClass().getName(),
                        event.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        }
    }
}
