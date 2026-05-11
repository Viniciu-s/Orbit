package com.orbit.lib.core;

import com.orbit.lib.api.CancellableEvent;
import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.Priority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusCancellableTest {

    // ---- Test event fixtures -----------------------------------------------

    static final class OrderEvent implements CancellableEvent {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final String id;

        OrderEvent(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    record PlainEvent(String value) implements Event {
    }

    // ---- Setup -------------------------------------------------------------

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new OrbitEventBus();
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    // ---- Cancellation stops the chain -------------------------------------

    @Test
    void should_notInvokeRemainingListeners_when_firstListenerCancelsEvent() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> {
            e.cancel();
            invoked.add("first");
        });
        bus.subscribe(OrderEvent.class, e -> invoked.add("second"));
        bus.subscribe(OrderEvent.class, e -> invoked.add("third"));

        bus.publish(new OrderEvent("O1"));

        assertEquals(List.of("first"), invoked);
    }

    @Test
    void should_invokeFirstListener_when_itCancelsEvent() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> {
            e.cancel();
            invoked.add("canceller");
        });

        bus.publish(new OrderEvent("O2"));

        assertEquals(List.of("canceller"), invoked);
    }

    @Test
    void should_invokeAllListeners_when_noneCancel() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> invoked.add("L1"));
        bus.subscribe(OrderEvent.class, e -> invoked.add("L2"));
        bus.subscribe(OrderEvent.class, e -> invoked.add("L3"));

        bus.publish(new OrderEvent("O3"));

        assertEquals(List.of("L1", "L2", "L3"), invoked);
    }

    @Test
    void should_notInvokeAnyListener_when_eventAlreadyCancelledBeforePublish() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> invoked.add("L1"));

        OrderEvent event = new OrderEvent("O4");
        event.cancel();
        bus.publish(event);

        assertTrue(invoked.isEmpty());
    }

    // ---- Plain events unaffected ------------------------------------------

    @Test
    void should_invokeAllListeners_when_eventIsNotCancellable() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(PlainEvent.class, e -> invoked.add("L1"));
        bus.subscribe(PlainEvent.class, e -> invoked.add("L2"));

        bus.publish(new PlainEvent("x"));

        assertEquals(List.of("L1", "L2"), invoked);
    }

    // ---- Priority + cancellation ------------------------------------------

    @Test
    void should_stopAfterHighPriorityListener_when_itCancels() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, Priority.NORMAL, e -> invoked.add("normal"));
        bus.subscribe(OrderEvent.class, Priority.HIGH, e -> {
            e.cancel();
            invoked.add("high");
        });
        bus.subscribe(OrderEvent.class, Priority.LOW, e -> invoked.add("low"));

        bus.publish(new OrderEvent("O5"));

        assertEquals(List.of("high"), invoked);
    }

    // ---- Error isolation + cancellation -----------------------------------

    @Test
    void should_continueCheckingCancellation_when_previousListenerThrew() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> {
            throw new RuntimeException("boom");
        });
        bus.subscribe(OrderEvent.class, e -> {
            e.cancel();
            invoked.add("canceller");
        });
        bus.subscribe(OrderEvent.class, e -> invoked.add("should-not-run"));

        assertDoesNotThrow(() -> bus.publish(new OrderEvent("O6")));

        assertEquals(List.of("canceller"), invoked);
    }

    // ---- State -------------------------------------------------------

    @Test
    void should_returnFalse_from_isCancelled_initially() {
        OrderEvent event = new OrderEvent("O7");
        assertFalse(event.isCancelled());
    }

    @Test
    void should_returnTrue_from_isCancelled_after_cancel() {
        OrderEvent event = new OrderEvent("O8");
        event.cancel();
        assertTrue(event.isCancelled());
    }

    // ---- publishAsync -----------------------------------------------------

    @Test
    void should_stopDispatch_when_eventCancelledDuringPublishAsync() throws Exception {
        List<String> invoked = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribe(OrderEvent.class, e -> {
            e.cancel();
            invoked.add("first");
            latch.countDown();
        });
        bus.subscribe(OrderEvent.class, e -> invoked.add("second"));

        bus.publishAsync(new OrderEvent("O9")).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("first"), invoked);
    }

    // ---- Independent publishes per event instance ------------------------

    @Test
    void should_treatEachPublishIndependently_when_separateEventInstances() {
        List<String> invoked = new ArrayList<>();
        bus.subscribe(OrderEvent.class, e -> {
            e.cancel();
            invoked.add("cancel-" + e.id());
        });
        bus.subscribe(OrderEvent.class, e -> invoked.add("after-" + e.id()));

        bus.publish(new OrderEvent("A"));
        bus.publish(new OrderEvent("B"));

        assertEquals(List.of("cancel-A", "cancel-B"), invoked);
    }

    // ---- Thread safety ---------------------------------------------------

    @Test
    void should_cancelSafely_when_calledConcurrently() throws Exception {
        AtomicBoolean didCancel = new AtomicBoolean(false);
        OrderEvent event = new OrderEvent("concurrent");
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    event.cancel();
                    if (event.isCancelled()) {
                        didCancel.set(true);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertTrue(didCancel.get());
        assertTrue(event.isCancelled());
    }
}
