package com.orbit.lib.api;

import java.util.Map;

/**
 * Provides read-only access to {@link EventBus} runtime metrics.
 *
 * <p>Metrics include:
 * <ul>
 *   <li>Event counts (total and per type)</li>
 *   <li>Execution time per event type</li>
 *   <li>Active listener counts</li>
 * </ul>
 *
 * <p>All metrics operations are thread-safe and can be queried concurrently
 * with event publishing.
 *
 * <p>Obtain an instance via {@link EventBus#metrics()}.
 */
public interface EventBusMetrics {

    /**
     * Returns the total number of events published since bus creation or last reset.
     *
     * <p>Includes both synchronous ({@link EventBus#publish}) and asynchronous
     * ({@link EventBus#publishAsync}) publications.
     *
     * @return total published event count; never negative
     */
    long totalEventsPublished();

    /**
     * Returns the number of events published for a specific event type.
     *
     * @param eventType the class token for the event type; must not be {@code null}
     * @return published event count for this type; {@code 0} if none published
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    long eventsPublished(Class<? extends Event> eventType);

    /**
     * Returns a snapshot of event counts grouped by simple class name.
     *
     * <p>The map key is the simple class name (e.g., "UserCreatedEvent"), and
     * the value is the publication count. The returned map is a defensive copy —
     * modifications do not affect internal state.
     *
     * @return an unmodifiable map of event counts by type; never {@code null}
     */
    Map<String, Long> eventCountByType();

    /**
     * Returns the execution time (in nanoseconds) of the most recent dispatch
     * for the given event type.
     *
     * <p>Execution time includes all listeners and interceptor {@code afterPublish}
     * calls, but excludes interceptor {@code beforePublish} calls.
     *
     * @param eventType the class token for the event type; must not be {@code null}
     * @return execution time in nanoseconds; {@code 0} if never published
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    long lastExecutionTimeNanos(Class<? extends Event> eventType);

    /**
     * Returns a snapshot of execution times grouped by simple class name.
     *
     * <p>Each value represents the last recorded execution time (in nanoseconds)
     * for that event type. The returned map is a defensive copy.
     *
     * @return an unmodifiable map of execution times by type; never {@code null}
     */
    Map<String, Long> executionTimeByType();

    /**
     * Returns the current number of listeners registered for the given event type.
     *
     * <p>This delegates to {@link EventBus#listenerCount} and reflects current
     * state, not historical metrics.
     *
     * @param eventType the class token; must not be {@code null}
     * @return active listener count; {@code 0} if none registered
     * @throws NullPointerException if {@code eventType} is {@code null}
     */
    int activeListeners(Class<? extends Event> eventType);

    /**
     * Returns the total number of type-specific listeners currently registered.
     *
     * <p>This sums listener counts across all event types but does <em>not</em>
     * include wildcard listeners registered via {@link EventBus#subscribeAll}.
     *
     * @return total active listener count; never negative
     */
    int totalActiveListeners();

    /**
     * Resets all historical metrics to zero.
     *
     * <p>This clears:
     * <ul>
     *   <li>Event counts (total and per type)</li>
     *   <li>Execution time records</li>
     * </ul>
     *
     * <p>Active listener counts are <em>not</em> reset, as they reflect current
     * state rather than historical data.
     *
     * <p>This operation is thread-safe but non-atomic: concurrent queries may
     * observe partially reset state during the operation.
     */
    void reset();
}
