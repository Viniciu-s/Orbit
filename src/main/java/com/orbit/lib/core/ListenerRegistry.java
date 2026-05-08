package com.orbit.lib.core;

import com.orbit.lib.api.EventListener;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal, thread-safe registry that maps event types to their listeners.
 *
 * <p>This class is an implementation detail of {@code OrbitEventBus} and is
 * intentionally package-private. It must not be exposed in the public API.
 *
 * <p>Thread safety is guaranteed by:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for concurrent access to the type-keyed map</li>
 *   <li>{@link CopyOnWriteArrayList} for safe concurrent registration and iteration</li>
 * </ul>
 */
final class ListenerRegistry {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventListener<?>>> store =
            new ConcurrentHashMap<>();

    /**
     * Registers a listener for the given event type.
     *
     * @param eventType the class token for the event; must not be {@code null}
     * @param listener  the handler to register; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    void register(Class<?> eventType, EventListener<?> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        store.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Returns a snapshot of all listeners registered for the given event type.
     *
     * <p>The returned list is a point-in-time copy — modifications to the
     * registry after this call are not reflected in the returned list.
     *
     * @param eventType the class token; must not be {@code null}
     * @return an unmodifiable snapshot, never {@code null}, may be empty
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    List<EventListener<?>> getListeners(Class<?> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        CopyOnWriteArrayList<EventListener<?>> listeners = store.get(eventType);
        if (listeners == null) {
            return Collections.emptyList();
        }
        return List.copyOf(listeners);
    }

    /**
     * Returns the number of listeners registered for the given event type.
     *
     * @param eventType the class token; must not be {@code null}
     * @return listener count, {@code 0} if none registered
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    int count(Class<?> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        CopyOnWriteArrayList<EventListener<?>> listeners = store.get(eventType);
        return listeners == null ? 0 : listeners.size();
    }
}
