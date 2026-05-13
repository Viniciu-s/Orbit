package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventBusMetrics;
import com.orbit.lib.api.EventInterceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects runtime metrics for an {@link EventBus} by intercepting events.
 *
 * <p>This interceptor implements {@link EventBusMetrics} and tracks:
 * <ul>
 *   <li>Event publication counts (total and per type)</li>
 *   <li>Execution time per event type</li>
 * </ul>
 *
 * <p>Thread safety is guaranteed by:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for per-type counters and timings</li>
 *   <li>{@link AtomicLong} for atomic counter increments</li>
 *   <li>{@link ThreadLocal} for correlating {@code beforePublish} and {@code afterPublish}</li>
 * </ul>
 *
 * <p>This class is package-private — obtain metrics via {@link EventBus#metrics()}.
 */
final class MetricsCollectorInterceptor implements EventInterceptor, EventBusMetrics {

    private final AtomicLong totalEvents = new AtomicLong(0);
    private final ConcurrentHashMap<Class<?>, AtomicLong> eventCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Long> executionTimes = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> startTimes = new ThreadLocal<>();
    private final EventBus bus;
    private final ListenerRegistry registry;

    /**
     * Creates a metrics collector tied to the given bus and registry.
     *
     * @param bus      the event bus to track metrics for; must not be {@code null}
     * @param registry the listener registry to query for listener counts; must not be {@code null}
     */
    MetricsCollectorInterceptor(EventBus bus, ListenerRegistry registry) {
        this.bus = Objects.requireNonNull(bus, "bus must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    // --- EventInterceptor implementation ---

    @Override
    public void beforePublish(Event event) {
        // Record start time for execution tracking
        startTimes.set(System.nanoTime());
    }

    @Override
    public void afterPublish(Event event) {
        // Increment counters
        totalEvents.incrementAndGet();
        eventCounts.computeIfAbsent(event.getClass(), k -> new AtomicLong(0))
                .incrementAndGet();

        // Record execution time
        Long startTime = startTimes.get();
        if (startTime != null) {
            long executionTime = System.nanoTime() - startTime;
            executionTimes.put(event.getClass(), executionTime);
            startTimes.remove(); // Clean up ThreadLocal
        }
    }

    // --- EventBusMetrics implementation ---

    @Override
    public long totalEventsPublished() {
        return totalEvents.get();
    }

    @Override
    public long eventsPublished(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        AtomicLong count = eventCounts.get(eventType);
        return count != null ? count.get() : 0L;
    }

    @Override
    public Map<String, Long> eventCountByType() {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<Class<?>, AtomicLong> entry : eventCounts.entrySet()) {
            snapshot.put(entry.getKey().getSimpleName(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public long lastExecutionTimeNanos(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Long time = executionTimes.get(eventType);
        return time != null ? time : 0L;
    }

    @Override
    public Map<String, Long> executionTimeByType() {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<Class<?>, Long> entry : executionTimes.entrySet()) {
            snapshot.put(entry.getKey().getSimpleName(), entry.getValue());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public int activeListeners(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return bus.listenerCount(eventType);
    }

    @Override
    public int totalActiveListeners() {
        // Sum listener counts across all registered event types
        int total = 0;
        for (Class<?> eventType : registry.getAllEventTypes()) {
            @SuppressWarnings("unchecked")
            Class<? extends Event> typedClass = (Class<? extends Event>) eventType;
            total += bus.listenerCount(typedClass);
        }
        return total;
    }

    @Override
    public void reset() {
        totalEvents.set(0);
        eventCounts.clear();
        executionTimes.clear();
        startTimes.remove(); // Clean up any lingering ThreadLocal
    }
}
