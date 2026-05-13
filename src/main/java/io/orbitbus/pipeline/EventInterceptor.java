package io.orbitbus.pipeline;

import io.orbitbus.core.Event;

/**
 * Intercepts events before and after they are dispatched to listeners.
 *
 * <p>Interceptors provide a hook to observe, validate, transform, or audit
 * events at the bus level — independently of individual listeners.
 *
 * <p>Execution order:
 * <ol>
 *   <li>{@link #beforePublish} is called on every registered interceptor (registration order)</li>
 *   <li>Listeners are invoked via {@code publish} or {@code publishAsync}</li>
 *   <li>{@link #afterPublish} is called on every registered interceptor (registration order)</li>
 * </ol>
 *
 * <p>If {@code beforePublish} throws an exception, the publish is aborted:
 * listeners are not invoked, and {@code afterPublish} is never called. The
 * exception propagates to the caller of {@code publish}.
 *
 * <p>If {@code afterPublish} throws an exception, it is isolated (caught and
 * logged) and does not interrupt other interceptors.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Logging / audit trails</li>
 *   <li>Metrics (start/stop timers)</li>
 *   <li>Validation (throw exception in {@code beforePublish} to block publish)</li>
 *   <li>Event enrichment</li>
 * </ul>
 */
public interface EventInterceptor {

    /**
     * Called before the event is dispatched to listeners.
     *
     * <p>This method is invoked synchronously on the publishing thread, even
     * when using {@code publishAsync}. If this method throws, the
     * publish is aborted.
     *
     * @param event the event about to be published; never {@code null}
     * @throws RuntimeException to abort the publish (listeners will not execute)
     */
    void beforePublish(Event event);

    /**
     * Called after the event has been dispatched to all listeners.
     *
     * <p>This method is invoked synchronously after listeners complete. If this
     * method throws, the exception is isolated and logged — it does not propagate
     * to the caller or interrupt other interceptors.
     *
     * @param event the event that was published; never {@code null}
     */
    void afterPublish(Event event);
}
