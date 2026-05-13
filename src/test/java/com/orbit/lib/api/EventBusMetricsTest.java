package com.orbit.lib.api;

import com.orbit.lib.Orbit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EventBusMetrics} functionality.
 *
 * <p>Verifies event counting, timing, and listener metrics are correctly tracked.
 */
class EventBusMetricsTest {

    record UserCreatedEvent(String name) implements Event { }

    record OrderPlacedEvent(String orderId) implements Event { }

    private EventBus bus;
    private EventBusMetrics metrics;

    @BeforeEach
    void setUp() {
        bus = Orbit.create();
        metrics = bus.metrics();
        assertNotNull(metrics, "Metrics should be available");
    }

    // --- Event Count Tests ---

    @Test
    void should_countTotalEventsPublished() {
        assertEquals(0, metrics.totalEventsPublished(), "Initial count should be 0");

        bus.publish(new UserCreatedEvent("Alice"));
        assertEquals(1, metrics.totalEventsPublished());

        bus.publish(new UserCreatedEvent("Bob"));
        assertEquals(2, metrics.totalEventsPublished());

        bus.publish(new OrderPlacedEvent("ORD-001"));
        assertEquals(3, metrics.totalEventsPublished());
    }

    @Test
    void should_countEventsPublishedByType() {
        assertEquals(0, metrics.eventsPublished(UserCreatedEvent.class));
        assertEquals(0, metrics.eventsPublished(OrderPlacedEvent.class));

        bus.publish(new UserCreatedEvent("Alice"));
        assertEquals(1, metrics.eventsPublished(UserCreatedEvent.class));
        assertEquals(0, metrics.eventsPublished(OrderPlacedEvent.class));

        bus.publish(new UserCreatedEvent("Bob"));
        bus.publish(new UserCreatedEvent("Charlie"));
        assertEquals(3, metrics.eventsPublished(UserCreatedEvent.class));

        bus.publish(new OrderPlacedEvent("ORD-001"));
        assertEquals(1, metrics.eventsPublished(OrderPlacedEvent.class));
    }

    @Test
    void should_provideEventCountByType() {
        bus.publish(new UserCreatedEvent("Alice"));
        bus.publish(new UserCreatedEvent("Bob"));
        bus.publish(new OrderPlacedEvent("ORD-001"));

        Map<String, Long> counts = metrics.eventCountByType();
        assertNotNull(counts);
        assertEquals(2L, counts.get("UserCreatedEvent"));
        assertEquals(1L, counts.get("OrderPlacedEvent"));
    }

    @Test
    void should_countAsyncEventsPublished() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        bus.subscribeAsync(UserCreatedEvent.class, event -> latch.countDown());

        bus.publishAsync(new UserCreatedEvent("Alice"));
        bus.publishAsync(new UserCreatedEvent("Bob"));
        bus.publishAsync(new UserCreatedEvent("Charlie"));

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Async events should complete");
        assertEquals(3, metrics.eventsPublished(UserCreatedEvent.class));
        assertEquals(3, metrics.totalEventsPublished());
    }

    // --- Timing Tests ---

    @Test
    void should_trackExecutionTime() {
        bus.subscribe(UserCreatedEvent.class, event -> {
            try {
                Thread.sleep(10); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        bus.publish(new UserCreatedEvent("Alice"));

        long executionTime = metrics.lastExecutionTimeNanos(UserCreatedEvent.class);
        assertTrue(executionTime > 0, "Execution time should be positive");
        assertTrue(executionTime >= TimeUnit.MILLISECONDS.toNanos(10),
                "Execution time should be at least 10ms");
    }

    @Test
    void should_trackExecutionTimeByType() {
        bus.subscribe(UserCreatedEvent.class, event -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        bus.publish(new UserCreatedEvent("Alice"));
        bus.publish(new OrderPlacedEvent("ORD-001"));

        Map<String, Long> timings = metrics.executionTimeByType();
        assertNotNull(timings);
        assertTrue(timings.containsKey("UserCreatedEvent"));
        assertTrue(timings.get("UserCreatedEvent") > 0);
    }

    @Test
    void should_returnZeroForUnpublishedEventTiming() {
        long executionTime = metrics.lastExecutionTimeNanos(UserCreatedEvent.class);
        assertEquals(0, executionTime, "Unpublished event should have 0 execution time");
    }

    // --- Listener Count Tests ---

    @Test
    void should_returnActiveListenerCount() {
        assertEquals(0, metrics.activeListeners(UserCreatedEvent.class));

        bus.subscribe(UserCreatedEvent.class, event -> { });
        assertEquals(1, metrics.activeListeners(UserCreatedEvent.class));

        bus.subscribe(UserCreatedEvent.class, event -> { });
        assertEquals(2, metrics.activeListeners(UserCreatedEvent.class));
    }

    @Test
    void should_returnTotalActiveListeners() {
        assertEquals(0, metrics.totalActiveListeners());

        bus.subscribe(UserCreatedEvent.class, event -> { });
        bus.subscribe(UserCreatedEvent.class, event -> { });
        bus.subscribe(OrderPlacedEvent.class, event -> { });

        assertEquals(3, metrics.totalActiveListeners());
    }

    // --- Reset Tests ---

    @Test
    void should_resetAllMetrics() {
        bus.subscribe(UserCreatedEvent.class, event -> { });
        bus.publish(new UserCreatedEvent("Alice"));
        bus.publish(new UserCreatedEvent("Bob"));

        assertEquals(2, metrics.totalEventsPublished());
        assertTrue(metrics.lastExecutionTimeNanos(UserCreatedEvent.class) > 0);

        metrics.reset();

        assertEquals(0, metrics.totalEventsPublished());
        assertEquals(0, metrics.eventsPublished(UserCreatedEvent.class));
        assertEquals(0, metrics.lastExecutionTimeNanos(UserCreatedEvent.class));
        assertTrue(metrics.eventCountByType().isEmpty());
        assertTrue(metrics.executionTimeByType().isEmpty());

        // Listener count should NOT be reset (it's current state, not historical)
        assertEquals(1, metrics.activeListeners(UserCreatedEvent.class));
    }

    // --- Thread Safety Tests ---

    @Test
    void should_handleConcurrentEventPublishing() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < eventsPerThread; j++) {
                        bus.publish(new UserCreatedEvent("User-" + j));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete");

        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));

        assertEquals(threadCount * eventsPerThread, metrics.totalEventsPublished(),
                "All concurrent events should be counted");
        assertEquals(threadCount * eventsPerThread, metrics.eventsPublished(UserCreatedEvent.class));
    }

    @Test
    void should_notCountInterceptorExceptions() {
        // Metrics interceptor should not break if other interceptors fail
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                throw new RuntimeException("Test exception");
            }

            @Override
            public void afterPublish(Event event) {
                // Not called because beforePublish throws
            }
        });

        try {
            bus.publish(new UserCreatedEvent("Alice"));
        } catch (RuntimeException e) {
            // Expected - beforePublish threw
        }

        // Metrics should still be 0 because publish was aborted
        assertEquals(0, metrics.totalEventsPublished(),
                "Aborted publish should not increment metrics");
    }
}
