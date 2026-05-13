package io.orbitbus.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusAsyncTest {

    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentEvent(double amount) implements Event {
    }

    /** Executor used to run async dispatches — synchronous in tests for determinism. */
    private ExecutorService executor;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        bus = new OrbitEventBus(executor);
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    // --- Happy path ---

    @Test
    void should_returnCompletableFuture_when_publishAsyncCalled() {
        bus.subscribe(UserCreatedEvent.class, e -> {
        });

        CompletableFuture<Void> future = bus.publishAsync(new UserCreatedEvent("Vinicius"));

        assertNotNull(future);
    }

    @Test
    void should_invokeListeners_when_futureCompletes() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        bus.subscribe(UserCreatedEvent.class, e -> invoked.set(true));

        bus.publishAsync(new UserCreatedEvent("Vinicius")).get(5, TimeUnit.SECONDS);

        assertTrue(invoked.get());
    }

    @Test
    void should_invokeAllListeners_when_futureCompletes() throws Exception {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());

        bus.publishAsync(new UserCreatedEvent("Vinicius")).get(5, TimeUnit.SECONDS);

        assertEquals(3, count.get());
    }

    @Test
    void should_notBlockCaller_when_publishAsyncCalled() throws Exception {
        CountDownLatch listenerStarted = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);

        bus.subscribe(UserCreatedEvent.class, e -> {
            listenerStarted.countDown();
            try {
                allowFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> future = bus.publishAsync(new UserCreatedEvent("Vinicius"));

        // caller continues immediately — future not yet done
        assertTrue(listenerStarted.await(5, TimeUnit.SECONDS), "listener should have started");
        assertTrue(!future.isDone(), "future should still be running while listener is blocked");

        allowFinish.countDown();
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void should_deliverEvent_to_correctListeners_only() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();

        bus.subscribe(UserCreatedEvent.class, e -> received.set(e.name()));
        bus.subscribe(PaymentEvent.class, e -> received.set("WRONG"));

        bus.publishAsync(new UserCreatedEvent("Vinicius")).get(5, TimeUnit.SECONDS);

        assertEquals("Vinicius", received.get());
    }

    // --- Error isolation ---

    @Test
    void should_completeNormally_when_listenerThrows() throws Exception {
        bus.subscribe(UserCreatedEvent.class, e -> {
            throw new RuntimeException("boom");
        });
        AtomicBoolean secondInvoked = new AtomicBoolean(false);
        bus.subscribe(UserCreatedEvent.class, e -> secondInvoked.set(true));

        CompletableFuture<Void> future = bus.publishAsync(new UserCreatedEvent("Vinicius"));
        future.get(5, TimeUnit.SECONDS);

        assertTrue(future.isDone() && !future.isCompletedExceptionally(),
                "future must complete normally even when a listener throws");
        assertTrue(secondInvoked.get(), "second listener must still be called");
    }

    // --- Concurrency ---

    @Test
    void should_handleConcurrentPublishAsync_without_losingEvents() throws Exception {
        int eventCount = 100;
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(eventCount);

        bus.subscribe(UserCreatedEvent.class, e -> {
            count.incrementAndGet();
            latch.countDown();
        });

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            futures.add(bus.publishAsync(new UserCreatedEvent("u" + i)));
        }

        latch.await(10, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        assertEquals(eventCount, count.get());
    }

    // --- Priority preserved in async ---

    @Test
    void should_respectPriorityOrder_when_publishAsync() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);

        // Use single-threaded executor so order is deterministic
        EventBus orderedBus = new OrbitEventBus(Executors.newSingleThreadExecutor());
        orderedBus.subscribe(UserCreatedEvent.class, io.orbitbus.annotation.Priority.LOW, e -> {
            order.add("LOW");
            latch.countDown();
        });
        orderedBus.subscribe(UserCreatedEvent.class, io.orbitbus.annotation.Priority.HIGH, e -> {
            order.add("HIGH");
            latch.countDown();
        });
        orderedBus.subscribe(UserCreatedEvent.class, io.orbitbus.annotation.Priority.NORMAL, e -> {
            order.add("NORMAL");
            latch.countDown();
        });

        orderedBus.publishAsync(new UserCreatedEvent("v")).get(5, TimeUnit.SECONDS);
        latch.await(5, TimeUnit.SECONDS);
        orderedBus.close();

        assertEquals(List.of("HIGH", "NORMAL", "LOW"), order);
    }

    // --- close / shutdown ---

    @Test
    void should_shutdownExecutor_when_closeCalled() throws Exception {
        bus.close();
        assertTrue(executor.isShutdown());
    }

    @Test
    void should_completeInflightFuture_before_executorTerminates() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);

        bus.subscribe(UserCreatedEvent.class, e -> {
            started.countDown();
            try {
                finish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> future = bus.publishAsync(new UserCreatedEvent("v"));
        started.await(5, TimeUnit.SECONDS);
        finish.countDown();
        future.get(5, TimeUnit.SECONDS);

        assertTrue(future.isDone());
    }

    // --- Null guard ---

    @Test
    void should_throwNullPointerException_when_publishAsyncReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.publishAsync(null));
    }

    // --- No listeners ---

    @Test
    void should_completeFuture_when_noListenersRegistered() throws Exception {
        CompletableFuture<Void> future = bus.publishAsync(new UserCreatedEvent("Vinicius"));
        assertNull(future.get(5, TimeUnit.SECONDS));
        assertTrue(future.isDone());
    }
}
