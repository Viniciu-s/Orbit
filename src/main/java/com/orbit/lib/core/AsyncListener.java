package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * An {@link EventListener} wrapper that dispatches every invocation to an
 * {@link ExecutorService}, making the listener execute off the calling thread.
 *
 * <p>Exceptions thrown by the delegate are caught and logged; they never
 * propagate to the caller of {@link #onEvent}.
 *
 * <p>{@link #equals} and {@link #hashCode} delegate to the inner listener so
 * that {@code EventBus.unsubscribe} can match by the original delegate
 * reference even though the registry stores the wrapper.
 *
 * @param <T> the event type
 */
final class AsyncListener<T extends Event> implements EventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncListener.class);

    private final EventListener<T> delegate;
    private final ExecutorService executor;

    AsyncListener(EventListener<T> delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    EventListener<T> delegate() {
        return delegate;
    }

    @Override
    public void onEvent(T event) {
        try {
            executor.execute(() -> {
                try {
                    delegate.onEvent(event);
                } catch (Exception e) {
                    LOG.error("Async listener [{}] failed while handling event [{}]: {}",
                            delegate.getClass().getName(), event.getClass().getSimpleName(),
                            e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warn("Async listener [{}] skipped: bus executor is shut down",
                    delegate.getClass().getName());
        }
    }

    /**
     * Two {@code AsyncListener} instances are equal when their delegates are equal.
     * An {@code AsyncListener} is also equal to its own delegate, enabling
     * {@code EventBus.unsubscribe(type, originalListener)} to locate and remove
     * the wrapper from the registry.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AsyncListener<?> other) {
            return delegate.equals(other.delegate);
        }
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
