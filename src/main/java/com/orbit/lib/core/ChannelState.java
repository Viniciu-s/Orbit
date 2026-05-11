package com.orbit.lib.core;

import com.orbit.lib.api.EventInterceptor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared state for a named channel.
 *
 * <p>Multiple {@link ChannelEventBus} instances for the same channel name
 * reference the same {@code ChannelState}, ensuring listener isolation.
 *
 * <p>Package-private.
 */
final class ChannelState {

    final ListenerRegistry registry = new ListenerRegistry();
    final Dispatcher dispatcher = new Dispatcher();
    final Map<Object, List<Runnable>> handlerDeregistrations =
            Collections.synchronizedMap(new IdentityHashMap<>());
    final CopyOnWriteArrayList<EventInterceptor> interceptors =
            new CopyOnWriteArrayList<>();

    ChannelState() {
    }
}
