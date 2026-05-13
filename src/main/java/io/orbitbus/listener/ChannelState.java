package io.orbitbus.listener;

import io.orbitbus.core.EventBus;
import io.orbitbus.pipeline.EventInterceptor;

import java.util.Collections;
import io.orbitbus.core.Dispatcher;
import io.orbitbus.metrics.MetricsCollectorInterceptor;
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
public final class ChannelState {

    final ListenerRegistry registry = new ListenerRegistry();
    final Dispatcher dispatcher = new Dispatcher();
    final Map<Object, List<Runnable>> handlerDeregistrations =
            Collections.synchronizedMap(new IdentityHashMap<>());
    final CopyOnWriteArrayList<EventInterceptor> interceptors =
            new CopyOnWriteArrayList<>();
    private MetricsCollectorInterceptor metricsCollector;

    public ChannelState() {
    }

    /**
     * Initializes metrics for this channel, if not already initialized.
     *
     * @param bus the EventBus to attach metrics to
     */
    void initializeMetrics(EventBus bus) {
        if (metricsCollector == null) {
            metricsCollector = new MetricsCollectorInterceptor(bus, registry);
            interceptors.add(metricsCollector);
        }
    }

    /**
     * Returns the metrics collector for this channel.
     *
     * @return the metrics collector, or {@code null} if not initialized
     */
    MetricsCollectorInterceptor getMetricsCollector() {
        return metricsCollector;
    }
}
