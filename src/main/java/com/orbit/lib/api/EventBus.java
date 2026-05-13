package com.orbit.lib.api;

import java.io.Closeable;

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
 *
 * <p>Implements {@link Closeable}: call {@link #close()} to release the internal
 * {@link java.util.concurrent.ExecutorService} used by {@link #publishAsync}.
 */
public interface EventBus extends Closeable {

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
     * Scans {@code handler} for public methods annotated with {@link Subscribe} and
     * registers each as a listener on this bus.
     *
     * <p>The event type, priority, and method signature are derived from the
     * annotation and the method parameter type. All annotated methods must have
     * exactly one parameter that implements {@link Event}.
     *
     * <p>Use {@link #unregister(Object)} to remove all listeners created by this call.
     *
     * @param handler the object whose {@code @Subscribe} methods should be registered;
     *                must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException     if {@code handler} is {@code null}
     * @throws IllegalArgumentException if any annotated method has an invalid signature
     */
    EventBus register(Object handler);

    /**
     * Removes all listeners that were registered by a prior {@link #register(Object)} call
     * for the given {@code handler} instance.
     *
     * <p>Matching is done by object identity ({@code ==}), not by {@code equals}, so
     * two handler instances of the same class are treated independently.
     *
     * <p>If the handler was never registered, or has already been unregistered,
     * this method does nothing (idempotent).
     *
     * @param handler the handler whose listeners should be removed; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    EventBus unregister(Object handler);

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
     * Registers a listener that always executes off the calling thread, even
     * when triggered by the synchronous {@link #publish} method.
     *
     * <p>The listener is submitted to the bus's internal
     * {@link java.util.concurrent.ExecutorService} on each invocation.
     * {@link #publish} returns before the listener finishes. Failures are
     * logged and isolated.
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param listener  the handler to invoke asynchronously; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code eventType} or {@code listener} is {@code null}
     */
    <T extends Event> EventBus subscribeAsync(Class<T> eventType, EventListener<T> listener);

    /**
     * Registers a listener that always executes off the calling thread, with an
     * explicit priority.
     *
     * @param <T>       the event type
     * @param eventType the class token for the event type; must not be {@code null}
     * @param priority  the execution priority; must not be {@code null}
     * @param listener  the handler to invoke asynchronously; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if any argument is {@code null}
     */
    <T extends Event> EventBus subscribeAsync(Class<T> eventType, Priority priority, EventListener<T> listener);

    /**
     * Registers a wildcard listener that is invoked for every published event,
     * regardless of its type.
     *
     * <p>The listener receives the base {@link Event} interface and can inspect
     * the concrete type using {@code instanceof}. Wildcard listeners are merged
     * with type-specific listeners and invoked in priority order (default: NORMAL).
     *
     * @param listener the handler to invoke for all events; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    EventBus subscribeAll(EventListener<Event> listener);

    /**
     * Registers a wildcard listener with an explicit priority.
     *
     * <p>Wildcard listeners are merged with type-specific listeners and invoked
     * in priority order. Listeners with the same priority are invoked in
     * registration order (FIFO).
     *
     * @param priority the execution priority; must not be {@code null}
     * @param listener the handler to invoke for all events; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if any argument is {@code null}
     */
    EventBus subscribeAll(Priority priority, EventListener<Event> listener);

    /**
     * Registers a wildcard listener that always executes off the calling thread.
     *
     * <p>The listener is submitted to the bus's internal
     * {@link java.util.concurrent.ExecutorService} on each invocation.
     *
     * @param listener the handler to invoke asynchronously for all events; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    EventBus subscribeAllAsync(EventListener<Event> listener);

    /**
     * Registers a wildcard listener that always executes off the calling thread,
     * with an explicit priority.
     *
     * @param priority the execution priority; must not be {@code null}
     * @param listener the handler to invoke asynchronously for all events; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if any argument is {@code null}
     */
    EventBus subscribeAllAsync(Priority priority, EventListener<Event> listener);

    /**
     * Removes a previously registered wildcard listener.
     *
     * <p>If the listener was registered multiple times, only one registration
     * is removed per call. If the listener is not registered, this method
     * does nothing (idempotent).
     *
     * @param listener the wildcard handler to remove; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code listener} is {@code null}
     */
    EventBus unsubscribeAll(EventListener<Event> listener);

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

    /**
     * Publishes an event asynchronously using an internal {@link java.util.concurrent.ExecutorService}.
     *
     * <p>Returns immediately; listeners are invoked off the calling thread.
     * Listener failures are logged and isolated — they never complete the future
     * exceptionally. Priority ordering is preserved within each async dispatch.
     *
     * @param <T>   the event type
     * @param event the event to publish; must not be {@code null}
     * @return a {@link java.util.concurrent.CompletableFuture} that completes when all
     *         listeners for this event have finished
     * @throws NullPointerException if {@code event} is {@code null}
     */
    <T extends Event> java.util.concurrent.CompletableFuture<Void> publishAsync(T event);

    /**
     * Sets a custom error handler that is called whenever a listener throws during
     * synchronous dispatch (including the synchronous phase of {@link #publishAsync}).
     *
     * <p>The handler fully replaces the default SLF4J logging. If the handler itself
     * throws, the exception is silently swallowed and logged internally.
     *
     * <p>Calling this method is thread-safe; the new handler takes effect immediately
     * for all subsequent dispatches.
     *
     * @param handler the error handler to set; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    EventBus setErrorHandler(ErrorHandler handler);

    /**
     * Registers an interceptor that will be invoked before and after each event is published.
     *
     * <p>Interceptors are invoked in the order they were added. If an interceptor's
     * {@link EventInterceptor#beforePublish} throws an exception, the publish is aborted.
     * If {@link EventInterceptor#afterPublish} throws, the exception is isolated and logged.
     *
     * @param interceptor the interceptor to add; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code interceptor} is {@code null}
     */
    EventBus addInterceptor(EventInterceptor interceptor);

    /**
     * Removes a previously registered interceptor.
     *
     * <p>If the interceptor is not currently registered, this method does nothing
     * (idempotent). Matching is done by identity ({@code ==}).
     *
     * @param interceptor the interceptor to remove; must not be {@code null}
     * @return this {@code EventBus} instance for fluent chaining
     * @throws NullPointerException if {@code interceptor} is {@code null}
     */
    EventBus removeInterceptor(EventInterceptor interceptor);

    /**
     * Creates an isolated event channel with the given name.
     *
     * <p>Each channel maintains its own set of listeners, interceptors, and error handler.
     * Events published to one channel are only delivered to listeners registered on that
     * same channel. The default (unnamed) bus and all named channels are completely isolated.
     *
     * <p>Calling {@code channel("payments")} multiple times returns separate {@code EventBus}
     * instances that share the same underlying channel state. Listeners registered via one
     * instance are visible to the other.
     *
     * <p>Channels share the same {@link java.util.concurrent.ExecutorService} as the parent
     * bus. When the parent bus is closed via {@link #close()}, all channels are closed as well.
     *
     * @param name the channel name; must not be {@code null}
     * @return an {@code EventBus} scoped to the named channel
     * @throws NullPointerException if {@code name} is {@code null}
     */
    EventBus channel(String name);

    /**
     * Returns runtime metrics for this event bus.
     *
     * <p>Metrics include event counts, execution times, and active listener counts.
     * All metrics operations are thread-safe and can be queried concurrently with
     * event publishing.
     *
     * <p>The returned {@link EventBusMetrics} instance is bound to this bus and
     * reflects its lifetime. Calling {@link EventBusMetrics#reset()} clears
     * historical metrics but does not affect active listeners.
     *
     * @return metrics for this bus; never {@code null}
     */
    EventBusMetrics metrics();

    /**
     * Shuts down the internal {@link java.util.concurrent.ExecutorService}.
     *
     * <p>Already-submitted tasks are allowed to complete. No new async publishes
     * should be issued after this call.
     */
    @Override
    void close();
}
