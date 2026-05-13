package com.orbit.lib.core;

import com.orbit.lib.api.ErrorHandler;
import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventBusMetrics;
import com.orbit.lib.api.EventInterceptor;
import com.orbit.lib.api.EventListener;
import com.orbit.lib.api.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe implementation of {@link EventBus}.
 *
 * <p>Delegates listener storage and retrieval to {@link ListenerRegistry}.
 */
public final class OrbitEventBus implements EventBus {

    private static final Logger LOG = LoggerFactory.getLogger(OrbitEventBus.class);

    private final ListenerRegistry registry = new ListenerRegistry();
    private final Dispatcher dispatcher = new Dispatcher();
    private final ExecutorService executor;
    private final MetricsCollectorInterceptor metricsCollector;
    private final Map<Object, List<Runnable>> handlerDeregistrations =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final CopyOnWriteArrayList<EventInterceptor> interceptors =
            new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, ChannelState> channels =
            new ConcurrentHashMap<>();

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
        this.metricsCollector = new MetricsCollectorInterceptor(this, registry);
        // Auto-register metrics collector as the first interceptor
        interceptors.add(metricsCollector);
    }

    @Override
    public EventBus setErrorHandler(ErrorHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        dispatcher.setErrorHandler(handler);
        return this;
    }

    @Override
    public EventBus addInterceptor(EventInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        interceptors.add(interceptor);
        LOG.debug("Added interceptor [{}], total: {}", interceptor.getClass().getSimpleName(), interceptors.size());
        return this;
    }

    @Override
    public EventBus removeInterceptor(EventInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        interceptors.remove(interceptor);
        LOG.debug("Removed interceptor [{}], remaining: {}", interceptor.getClass().getSimpleName(), interceptors.size());
        return this;
    }

    @Override
    public EventBus register(Object handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        List<ScannedMethod> scanned = AnnotationScanner.scan(handler);
        LOG.debug("Registering handler [{}], found {} @Subscribe method(s)",
                handler.getClass().getSimpleName(), scanned.size());
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
            LOG.debug("Unregistered handler [{}], removed {} listener(s)",
                    handler.getClass().getSimpleName(), deregistrations.size());
        }
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        registry.register(eventType, listener);
        LOG.debug("Subscribed listener for event type [{}], total listeners: {}",
                eventType.getSimpleName(), registry.count(eventType));
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
        LOG.debug("Subscribed async listener for event type [{}]", eventType.getSimpleName());
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
    public EventBus subscribeAll(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        registry.registerWildcard(listener, Priority.NORMAL);
        return this;
    }

    @Override
    public EventBus subscribeAll(Priority priority, EventListener<Event> listener) {
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        registry.registerWildcard(listener, priority);
        return this;
    }

    @Override
    public EventBus subscribeAllAsync(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        registry.registerWildcard(new AsyncListener<>(listener, executor), Priority.NORMAL);
        return this;
    }

    @Override
    public EventBus subscribeAllAsync(Priority priority, EventListener<Event> listener) {
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        registry.registerWildcard(new AsyncListener<>(listener, executor), priority);
        return this;
    }

    @Override
    public EventBus unsubscribeAll(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        registry.deregisterWildcard(listener);
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
        LOG.debug("Unsubscribed listener for event type [{}], remaining: {}",
                eventType.getSimpleName(), registry.count(eventType));
        return this;
    }

    @Override
    public <T extends Event> EventBus publish(T event) {
        Objects.requireNonNull(event, "event must not be null");

        // beforePublish (can throw to abort)
        for (EventInterceptor interceptor : interceptors) {
            interceptor.beforePublish(event);
        }

        // dispatch to listeners
        List<EventListener<?>> listeners = registry.getListeners(event.getClass());
        LOG.info("Publishing event [{}] to {} listener(s)", event.getClass().getSimpleName(), listeners.size());
        dispatcher.dispatch(event, listeners);

        // afterPublish (isolated)
        for (EventInterceptor interceptor : interceptors) {
            try {
                interceptor.afterPublish(event);
            } catch (Exception e) {
                dispatcher.reportError(e);
            }
        }

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

        // beforePublish (synchronous, can throw to abort)
        for (EventInterceptor interceptor : interceptors) {
            interceptor.beforePublish(event);
        }

        List<EventListener<?>> listeners = registry.getListeners(event.getClass());
        LOG.info("Publishing event [{}] asynchronously to {} listener(s)",
                event.getClass().getSimpleName(), listeners.size());
        return CompletableFuture.runAsync(
                () -> {
                    // dispatch to listeners
                    dispatcher.dispatch(event, listeners);

                    // afterPublish (isolated)
                    for (EventInterceptor interceptor : interceptors) {
                        try {
                            interceptor.afterPublish(event);
                        } catch (Exception e) {
                            dispatcher.reportError(e);
                        }
                    }
                },
                executor
        );
    }

    @Override
    public EventBus channel(String name) {
        Objects.requireNonNull(name, "name must not be null");
        ChannelState state = channels.computeIfAbsent(name, k -> {
            LOG.debug("Creating new channel [{}]", name);
            return new ChannelState();
        });
        return new ChannelEventBus(name, state, executor);
    }

    @Override
    public EventBusMetrics metrics() {
        return metricsCollector;
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
