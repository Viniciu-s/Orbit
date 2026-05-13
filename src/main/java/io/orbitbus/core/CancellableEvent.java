package io.orbitbus.core;

/**
 * An {@link Event} that can be cancelled during dispatch.
 *
 * <p>When a listener calls {@link #cancel()}, the bus stops invoking any
 * remaining listeners for that publish call. The cancelling listener itself
 * is always fully executed before the check occurs.
 *
 * <p>If the event is already cancelled before {@link EventBus#publish} is
 * called, no listeners are invoked at all.
 *
 * <p>Implementations are responsible for making the cancelled state
 * thread-safe when the event may be published asynchronously. Using an
 * {@link java.util.concurrent.atomic.AtomicBoolean} is recommended:
 *
 * <pre>{@code
 * public class OrderEvent implements CancellableEvent {
 *     private final AtomicBoolean cancelled = new AtomicBoolean(false);
 *
 *     @Override public void cancel()           { cancelled.set(true); }
 *     @Override public boolean isCancelled()   { return cancelled.get(); }
 * }
 * }</pre>
 */
public interface CancellableEvent extends Event {

    /**
     * Marks this event as cancelled.
     *
     * <p>The bus will not invoke any listeners registered after the one that
     * called this method.
     */
    void cancel();

    /**
     * Returns {@code true} if this event has been cancelled.
     *
     * @return {@code true} if cancelled, {@code false} otherwise
     */
    boolean isCancelled();
}
