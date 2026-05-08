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
     * Registers a listener for events of the given type with an explicit priority.
     *
     * <p>Listeners are invoked in priority order: {@link Priority#HIGH} first,
     * {@link Priority#NORMAL} in the middle, and {@link Priority#LOW} last.
     * Listeners with the same priority are invoked in registration order (FIFO).
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param priority  the execution priority; must not be {@code null}
     * @param listener  the handler to invoke; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if any argument is {@code null}
     */
    <T extends Event> EventBus subscribe(Class<T> eventType, Priority priority, EventListener<T> listener);

    /**
     * Registers a one-shot listener that is automatically removed after its
     * first invocation.
     *
     * <p>Even under concurrent publishing, the listener fires at most once.
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param listener  the handler to invoke once; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code eventType} or {@code listener} is {@code null}
     */
    <T extends Event> EventBus once(Class<T> eventType, EventListener<T> listener);

    /**
     * Removes a previously registered listener for the given event type.
     *
     * <p>If the listener was registered multiple times, only one registration
     * is removed per call. If the listener is not registered, this method
     * does nothing (idempotent).
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param listener  the handler to remove; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code eventType} or {@code listener} is {@code null}
     */
    <T extends Event> EventBus unsubscribe(Class<T> eventType, EventListener<T> listener);

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
