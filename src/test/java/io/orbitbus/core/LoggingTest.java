package io.orbitbus.core;

import io.orbitbus.Orbit;
import io.orbitbus.annotation.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that internal logging does not interfere with normal EventBus operations.
 *
 * <p>This test ensures that:
 * <ul>
 *   <li>Logger initialization does not throw exceptions</li>
 *   <li>Log statements execute without errors</li>
 *   <li>Logging does not affect event dispatch behavior</li>
 * </ul>
 */
class LoggingTest {

    record TestEvent(String message) implements Event { }

    @Test
    void loggingDoesNotBreakEventDispatch() {
        EventBus bus = Orbit.create();
        AtomicInteger counter = new AtomicInteger(0);

        // All these operations should log at DEBUG/INFO level without throwing
        assertDoesNotThrow(() -> {
            bus.subscribe(TestEvent.class, event -> counter.incrementAndGet());
            bus.publish(new TestEvent("test"));
            bus.unsubscribe(TestEvent.class, event -> { });
        });

        assertEquals(1, counter.get(), "Event should have been dispatched");
    }

    @Test
    void loggingDoesNotBreakAsyncDispatch() throws InterruptedException {
        EventBus bus = Orbit.create();
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        assertDoesNotThrow(() -> {
            bus.subscribeAsync(TestEvent.class, event -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            bus.publishAsync(new TestEvent("test"));
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Async event should complete within timeout");
        assertEquals(1, counter.get(), "Async event should have been dispatched");
    }

    @Test
    void loggingDoesNotBreakAnnotationScanning() {
        EventBus bus = Orbit.create();
        TestHandler handler = new TestHandler();

        // Scanning and registration should log without throwing
        assertDoesNotThrow(() -> {
            bus.register(handler);
            bus.publish(new TestEvent("test"));
            bus.unregister(handler);
        });

        assertEquals(1, handler.counter.get(), "Handler should have received event");
    }

    @Test
    void loggingDoesNotBreakOneShotListener() {
        EventBus bus = Orbit.create();
        AtomicInteger counter = new AtomicInteger(0);

        assertDoesNotThrow(() -> {
            bus.once(TestEvent.class, event -> counter.incrementAndGet());
            bus.publish(new TestEvent("test1"));
            bus.publish(new TestEvent("test2"));
        });

        assertEquals(1, counter.get(), "One-shot listener should fire only once");
    }

    public static class TestHandler {
        AtomicInteger counter = new AtomicInteger(0);

        @Subscribe
        public void onTestEvent(TestEvent event) {
            counter.incrementAndGet();
        }
    }
}
