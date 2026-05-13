package com.orbit.lib.core;

import com.orbit.lib.api.EventListener;
import com.orbit.lib.api.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private static final Logger LOG = LoggerFactory.getLogger(ListenerRegistry.class);

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<PrioritizedListener<?>>> store =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PrioritizedListener<?>> wildcardListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Registers a listener for the given event type with {@link Priority#NORMAL}.
     *
     * @param eventType the class token for the event; must not be {@code null}
     * @param listener  the handler to register; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    void register(Class<?> eventType, EventListener<?> listener) {
        register(eventType, listener, Priority.NORMAL);
    }

    /**
     * Registers a listener for the given event type with an explicit priority.
     *
     * @param eventType the class token for the event; must not be {@code null}
     * @param listener  the handler to register; must not be {@code null}
     * @param priority  the execution priority; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    void register(Class<?> eventType, EventListener<?> listener, Priority priority) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        store.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new PrioritizedListener<>(priority, listener));
        LOG.debug("Registered listener for type [{}] with priority [{}]",
                eventType.getSimpleName(), priority);
    }

    /**
     * Registers a wildcard listener that receives all events.
     *
     * @param listener the handler to register; must not be {@code null}
     * @param priority the execution priority; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    void registerWildcard(EventListener<?> listener, Priority priority) {
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        wildcardListeners.add(new PrioritizedListener<>(priority, listener));
        LOG.debug("Registered wildcard listener with priority [{}]", priority);
    }

    /**
     * Removes one registration of the given wildcard listener.
     *
     * @param listener the handler to remove; must not be {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    void deregisterWildcard(EventListener<?> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        for (PrioritizedListener<?> pl : wildcardListeners) {
            if (pl.delegate().equals(listener)) {
                wildcardListeners.remove(pl);
                LOG.debug("Deregistered wildcard listener");
                return;
            }
        }
    }

    /**
     * Removes one registration of the given listener for the given event type.
     *
     * <p>The match is made against the wrapped delegate — the original listener
     * instance passed to {@link #register} — regardless of its priority.
     * If the listener is registered multiple times, only the first matching
     * entry is removed. If the listener is not registered, this method does
     * nothing (idempotent).
     *
     * @param eventType the class token for the event; must not be {@code null}
     * @param listener  the handler to remove; must not be {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    void deregister(Class<?> eventType, EventListener<?> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        CopyOnWriteArrayList<PrioritizedListener<?>> listeners = store.get(eventType);
        if (listeners == null) {
            return;
        }
        for (PrioritizedListener<?> pl : listeners) {
            if (pl.delegate().equals(listener)) {
                listeners.remove(pl);
                LOG.debug("Deregistered listener for type [{}]", eventType.getSimpleName());
                return;
            }
        }
    }

    /**
     * Returns a priority-sorted snapshot of all listeners for the given event type,
     * including wildcard listeners.
     *
     * <p>Wildcard listeners are merged with type-specific listeners and sorted
     * by {@link Priority} (HIGH first, LOW last). Within the same priority,
     * insertion order (FIFO) is preserved. The returned list is a point-in-time
     * copy — modifications after this call are not reflected.
     *
     * @param eventType the class token; must not be {@code null}
     * @return an unmodifiable, priority-ordered snapshot; never {@code null}
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    List<EventListener<?>> getListeners(Class<?> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        CopyOnWriteArrayList<PrioritizedListener<?>> specificListeners = store.get(eventType);

        // Merge wildcard + specific
        List<PrioritizedListener<?>> merged = new ArrayList<>(wildcardListeners);
        if (specificListeners != null) {
            merged.addAll(specificListeners);
        }

        if (merged.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by priority
        merged.sort(Comparator.comparingInt(pl -> pl.priority().ordinal()));

        // Extract delegates
        List<EventListener<?>> result = new ArrayList<>(merged.size());
        for (PrioritizedListener<?> pl : merged) {
            result.add(pl);
        }
        return Collections.unmodifiableList(result);
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
        CopyOnWriteArrayList<PrioritizedListener<?>> listeners = store.get(eventType);
        return listeners == null ? 0 : listeners.size();
    }

    /**
     * Returns a snapshot of all event types that have at least one listener registered.
     *
     * <p>The returned set is a defensive copy — modifications do not affect
     * the internal state.
     *
     * @return all event types with registered listeners; never {@code null}
     */
    java.util.Set<Class<?>> getAllEventTypes() {
        return new java.util.HashSet<>(store.keySet());
    }
}
