package io.orbitbus.core;

import io.orbitbus.exception.ErrorHandler;
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
public final class Dispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);

    private volatile ErrorHandler errorHandler =
            e -> LOG.error("A listener failed while handling an event", e);

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    /**
     * Reports an error through the configured {@link ErrorHandler}.
     *
     * <p>Used internally by {@code OrbitEventBus} to report interceptor failures.
     *
     * @param error the error to report; must not be {@code null}
     */
    public void reportError(Throwable error) {
        try {
            errorHandler.onError(error);
        } catch (Exception handlerEx) {
            LOG.error("ErrorHandler threw while handling an error", handlerEx);
        }
    }

    /**
     * Dispatches {@code event} to every listener in {@code listeners}.
     *
     * @param <T>       the event type
     * @param event     the event to dispatch; must not be {@code null}
     * @param listeners snapshot of listeners to invoke; must not be {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void dispatch(T event, List<EventListener<?>> listeners) {
        LOG.debug("Dispatching event [{}] to {} listener(s)", event.getClass().getSimpleName(), listeners.size());
        for (EventListener<?> listener : listeners) {
            if (event instanceof CancellableEvent ce && ce.isCancelled()) {
                break;
            }
            try {
                ((EventListener<T>) listener).onEvent(event);
            } catch (Exception e) {
                try {
                    errorHandler.onError(e);
                } catch (Exception handlerEx) {
                    LOG.error("ErrorHandler threw while handling a listener failure", handlerEx);
                }
            }
        }
    }
}
