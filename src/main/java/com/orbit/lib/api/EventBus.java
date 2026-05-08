package com.orbit.lib.api;

/**
 * Public contract for the Orbit Event Bus.
 *
 * <p>Implementations must be thread-safe. All methods return {@code this}
 * to support fluent chaining:
 *
 * <pre>{@code
 * Orbit.create()
 *     .subscribe(UserEvent.class, listener)
 *     .publish(event);
 * }</pre>
 */
public interface EventBus {

    /**
     * Registers a listener for events of the given type.
     *
     * <p>Multiple listeners can be registered for the same event type.
     * They are invoked in registration order.
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param listener  the handler to invoke when the event is published; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code eventType} or {@code listener} is {@code null}
     */
    <T extends Event> EventBus subscribe(Class<T> eventType, EventListener<T> listener);

    /**
     * Publishes an event to all listeners registered for its type.
     *
     * <p>Listeners are invoked synchronously in registration order.
     * A failing listener is logged and skipped — it never interrupts
     * the remaining listeners or propagates an exception to the caller.
     *
     * @param <T>   the event type
     * @param event the event to publish; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code event} is {@code null}
     */
    <T extends Event> EventBus publish(T event);

    /**
     * Returns the number of listeners currently registered for the given event type.
     *
     * @param eventType the class token; must not be {@code null}
     * @return listener count, or {@code 0} if none registered
     */
    int listenerCount(Class<? extends Event> eventType);
}
