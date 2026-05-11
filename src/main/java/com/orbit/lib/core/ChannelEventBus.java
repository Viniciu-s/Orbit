package com.orbit.lib.core;

import com.orbit.lib.api.ErrorHandler;
import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventInterceptor;
import com.orbit.lib.api.EventListener;
import com.orbit.lib.api.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of {@link EventBus} for a named channel.
 *
 * <p>Each channel is isolated: listeners and events are scoped to the channel.
 * Channels share the {@link ExecutorService} with the parent bus but maintain
 * their own {@link ListenerRegistry}, interceptors, and error handler via
 * a shared {@link ChannelState}.
 *
 * <p>Package-private — created via {@link OrbitEventBus#channel(String)}.
 */
final class ChannelEventBus implements EventBus {

    private final ChannelState state;
    private final ExecutorService executor;

    /**
     * Creates a channel-scoped event bus.
     *
     * @param name     the channel name; must not be {@code null}
     * @param state    the shared channel state; must not be {@code null}
     * @param executor the shared executor; must not be {@code null}
     */
    ChannelEventBus(String name, ChannelState state, ExecutorService executor) {
        Objects.requireNonNull(name, "name must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public EventBus setErrorHandler(ErrorHandler handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        state.dispatcher.setErrorHandler(handler);
        return this;
    }

    @Override
    public EventBus addInterceptor(EventInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        state.interceptors.add(interceptor);
        return this;
    }

    @Override
    public EventBus removeInterceptor(EventInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        state.interceptors.remove(interceptor);
        return this;
    }

    @Override
    public EventBus register(Object handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        List<ScannedMethod> scanned = AnnotationScanner.scan(handler);
        List<Runnable> deregistrations = new ArrayList<>();
        for (ScannedMethod sm : scanned) {
            EventListener<?> ml = new MethodListener<>(handler, sm.method());
            state.registry.register(sm.eventType(), ml, sm.priority());
            final Class<?> type = sm.eventType();
            final EventListener<?> finalMl = ml;
            deregistrations.add(() -> state.registry.deregister(type, finalMl));
        }
        state.handlerDeregistrations.put(handler, deregistrations);
        return this;
    }

    @Override
    public EventBus unregister(Object handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        List<Runnable> deregistrations = state.handlerDeregistrations.remove(handler);
        if (deregistrations != null) {
            deregistrations.forEach(Runnable::run);
        }
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        state.registry.register(eventType, listener);
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribe(Class<T> eventType, Priority priority, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        state.registry.register(eventType, listener, priority);
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribeAsync(Class<T> eventType, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        state.registry.register(eventType, new AsyncListener<>(listener, executor));
        return this;
    }

    @Override
    public <T extends Event> EventBus subscribeAsync(Class<T> eventType, Priority priority, EventListener<T> listener) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        state.registry.register(eventType, new AsyncListener<>(listener, executor), priority);
        return this;
    }

    @Override
    public EventBus subscribeAll(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        state.registry.registerWildcard(listener, Priority.NORMAL);
        return this;
    }

    @Override
    public EventBus subscribeAll(Priority priority, EventListener<Event> listener) {
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        state.registry.registerWildcard(listener, priority);
        return this;
    }

    @Override
    public EventBus subscribeAllAsync(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        state.registry.registerWildcard(new AsyncListener<>(listener, executor), Priority.NORMAL);
        return this;
    }

    @Override
    public EventBus subscribeAllAsync(Priority priority, EventListener<Event> listener) {
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        state.registry.registerWildcard(new AsyncListener<>(listener, executor), priority);
        return this;
    }

    @Override
    public EventBus unsubscribeAll(EventListener<Event> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        state.registry.deregisterWildcard(listener);
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

        state.registry.deregister(eventType, listener);
        return this;
    }

    @Override
    public <T extends Event> EventBus publish(T event) {
        Objects.requireNonNull(event, "event must not be null");

        // beforePublish (can throw to abort)
        for (EventInterceptor interceptor : state.interceptors) {
            interceptor.beforePublish(event);
        }

        // dispatch to listeners
        state.dispatcher.dispatch(event, state.registry.getListeners(event.getClass()));

        // afterPublish (isolated)
        for (EventInterceptor interceptor : state.interceptors) {
            try {
                interceptor.afterPublish(event);
            } catch (Exception e) {
                state.dispatcher.reportError(e);
            }
        }

        return this;
    }

    @Override
    public int listenerCount(Class<? extends Event> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return state.registry.count(eventType);
    }

    @Override
    public <T extends Event> CompletableFuture<Void> publishAsync(T event) {
        Objects.requireNonNull(event, "event must not be null");

        // beforePublish (synchronous, can throw to abort)
        for (EventInterceptor interceptor : state.interceptors) {
            interceptor.beforePublish(event);
        }

        List<EventListener<?>> listeners = state.registry.getListeners(event.getClass());
        return CompletableFuture.runAsync(
                () -> {
                    // dispatch to listeners
                    state.dispatcher.dispatch(event, listeners);

                    // afterPublish (isolated)
                    for (EventInterceptor interceptor : state.interceptors) {
                        try {
                            interceptor.afterPublish(event);
                        } catch (Exception e) {
                            state.dispatcher.reportError(e);
                        }
                    }
                },
                executor
        );
    }

    @Override
    public EventBus channel(String channelName) {
        Objects.requireNonNull(channelName, "name must not be null");
        return this;
    }

    @Override
    public void close() {
        // Channels don't own the executor — parent bus closes it
    }
}
