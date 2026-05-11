package com.orbit.lib.core;

import com.orbit.lib.api.ErrorHandler;
import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventListener;
import com.orbit.lib.api.Priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe implementation of {@link EventBus}.
 *
 * <p>Delegates listener storage and retrieval to {@link ListenerRegistry}.
 */
public final class OrbitEventBus implements EventBus {

    private final ListenerRegistry registry = new ListenerRegistry();
    private final Dispatcher dispatcher = new Dispatcher();
    private final ExecutorService executor;
    private final Map<Object, List<Runnable>> handlerDeregistrations =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Creates a bus backed by a cached thread pool. */
    public OrbitEventBus() {
        this(Executors.newCachedThreadPool());
    }

    /**
     * Creates a bus backed by the supplied executor.
     *
     * <p>Useful for testing: pass a direct / single-thread executor
     * to make async dispatch deterministic.
     *
     * @param executor the executor to use for async dispatch; must not be {@code null}
     */
    public OrbitEventBus(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public EventBus setErrorHandler(ErrorHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        dispatcher.setErrorHandler(handler);
        return this;
    }

    @Override
    public EventBus register(Object handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        List<ScannedMethod> scanned = AnnotationScanner.scan(handler);
        List<Runnable> deregistrations = new ArrayList<>();
        for (ScannedMethod sm : scanned) {
            EventListener<?> ml = new MethodListener<>(handler, sm.method());
            registry.register(sm.eventType(), ml, sm.priority());
            final Class<?> type = sm.eventType();
            final EventListener<?> finalMl = ml;
            deregistrations.add(() -> registry.deregister(type, finalMl));
        }
        handlerDeregistrations.put(handler, deregistrations);
        return this;
    }

    @Override
    public EventBus unregister(Object handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        List<Runnable> deregistrations = handlerDeregistrations.remove(handler);
        if (deregistrations != null) {
            deregistrations.forEach(Runnable::run);
        }
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, listener);
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, Priority priority, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, listener, priority);
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribeAsync(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, new AsyncListener<>(listener, executor));
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribeAsync(Class<T> eventType, Priority priority, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, new AsyncListener<>(listener, executor), priority);
        return this;
    }

    @Override
    public <T extends Event> EventBus once(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        subscribe(eventType, new OneShotListener<>(eventType, listener, this));
        return this;
    }

    @Override
    public <T extends Event> EventBus unsubscribe(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.deregister(eventType, listener);
        return this;
    }

    @Override
    public <T extends Event> EventBus publish(T event) {
        Objects.requireNonNull(event, "event must not be null");

        dispatcher.dispatch(event, registry.getListeners(event.getClass()));
        return this;
    }

    @Override
    public int listenerCount(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return registry.count(eventType);
    }

    @Override
    public <T extends Event> CompletableFuture<Void> publishAsync(T event) {
        Objects.requireNonNull(event, "event must not be null");
        List<EventListener<?>> listeners = registry.getListeners(event.getClass());
        return CompletableFuture.runAsync(
                () -> dispatcher.dispatch(event, listeners),
                executor
        );
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
