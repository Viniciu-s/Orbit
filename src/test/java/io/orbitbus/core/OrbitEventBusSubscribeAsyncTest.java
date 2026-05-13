package io.orbitbus.core;

import io.orbitbus.annotation.Priority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusSubscribeAsyncTest {

    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentEvent(double amount) implements Event {
    }

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

    // --- Off-caller-thread execution ---

    @Test
    void should_invokeListener_on_differentThread_when_publishCalled() throws Exception {
        AtomicReference<String> listenerThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        String callerThread = Thread.currentThread().getName();

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            listenerThread.set(Thread.currentThread().getName());
            latch.countDown();
        });

        bus.publish(new UserCreatedEvent("Vinicius"));
        latch.await(5, TimeUnit.SECONDS);

        assertNotEquals(callerThread, listenerThread.get(),
                "async listener must run on a different thread than the caller");
    }

    @Test
    void should_invokeListener_on_differentThread_when_publishAsyncCalled() throws Exception {
        AtomicReference<String> listenerThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        String callerThread = Thread.currentThread().getName();

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            listenerThread.set(Thread.currentThread().getName());
            latch.countDown();
        });

        bus.publishAsync(new UserCreatedEvent("Vinicius")).get(5, TimeUnit.SECONDS);
        latch.await(5, TimeUnit.SECONDS);

        assertNotEquals(callerThread, listenerThread.get());
    }

    // --- publish() does not block on async listeners ---

    @Test
    void should_returnBeforeListenerFinishes_when_syncPublishWithAsyncListener() throws Exception {
        CountDownLatch listenerStarted = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        AtomicBoolean publishReturned = new AtomicBoolean(false);

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            listenerStarted.countDown();
            try {
                allowFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        // publish() should return immediately (listener blocked in executor)
        bus.publish(new UserCreatedEvent("Vinicius"));
        publishReturned.set(true);

        assertTrue(listenerStarted.await(5, TimeUnit.SECONDS));
        assertTrue(publishReturned.get(), "publish() must return before async listener finishes");

        allowFinish.countDown();
    }

    // --- Listener receives correct event ---

    @Test
    void should_invokeListenerWithCorrectEvent_when_subscribeAsync() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            received.set(e.name());
            latch.countDown();
        });

        bus.publish(new UserCreatedEvent("Vinicius"));
        latch.await(5, TimeUnit.SECONDS);

        assertEquals("Vinicius", received.get());
    }

    // --- Mix of sync and async listeners for same event type ---

    @Test
    void should_invokeAllListeners_when_mixOfSyncAndAsync() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch asyncLatch = new CountDownLatch(1);

        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            count.incrementAndGet();
            asyncLatch.countDown();
        });
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));
        asyncLatch.await(5, TimeUnit.SECONDS);

        assertEquals(3, count.get());
    }

    // --- Error isolation ---

    @Test
    void should_notAffectOtherListeners_when_asyncListenerThrows() throws Exception {
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicBoolean secondInvoked = new AtomicBoolean(false);

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            throw new RuntimeException("async boom");
        });
        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            secondInvoked.set(true);
            secondLatch.countDown();
        });

        bus.publish(new UserCreatedEvent("Vinicius"));
        secondLatch.await(5, TimeUnit.SECONDS);

        assertTrue(secondInvoked.get(), "second async listener must still fire");
    }

    // --- Priority ---

    @Test
    void should_respectPriority_when_subscribeAsyncWithPriority() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);

        // Single-thread executor for deterministic ordering
        EventBus orderedBus = new OrbitEventBus(Executors.newSingleThreadExecutor());

        orderedBus.subscribeAsync(UserCreatedEvent.class, Priority.LOW, e -> {
            order.add("LOW");
            latch.countDown();
        });
        orderedBus.subscribeAsync(UserCreatedEvent.class, Priority.HIGH, e -> {
            order.add("HIGH");
            latch.countDown();
        });
        orderedBus.subscribeAsync(UserCreatedEvent.class, Priority.NORMAL, e -> {
            order.add("NORMAL");
            latch.countDown();
        });

        orderedBus.publish(new UserCreatedEvent("Vinicius"));
        latch.await(5, TimeUnit.SECONDS);
        orderedBus.close();

        assertEquals(List.of("HIGH", "NORMAL", "LOW"), order);
    }

    @Test
    void should_returnSameBusInstance_when_subscribeAsyncWithPriorityCalled() {
        EventBus result = bus.subscribeAsync(UserCreatedEvent.class, Priority.HIGH, e -> {
        });
        assertSame(bus, result);
    }

    // --- Unsubscribe ---

    @Test
    void should_unsubscribe_asyncListener_using_original_delegate() throws Exception {
        AtomicInteger count = new AtomicInteger();
        EventListener<UserCreatedEvent> listener = e -> count.incrementAndGet();

        bus.subscribeAsync(UserCreatedEvent.class, listener);
        bus.unsubscribe(UserCreatedEvent.class, listener);

        bus.publish(new UserCreatedEvent("Vinicius"));
        // brief pause to confirm listener does NOT run
        Thread.sleep(50);

        assertEquals(0, count.get(), "unsubscribed async listener must not fire");
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    // --- listenerCount ---

    @Test
    void should_countAsyncListeners_correctly() {
        bus.subscribeAsync(UserCreatedEvent.class, e -> {
        });
        bus.subscribeAsync(UserCreatedEvent.class, e -> {
        });

        assertEquals(2, bus.listenerCount(UserCreatedEvent.class));
    }

    // --- Fluent ---

    @Test
    void should_returnSameBusInstance_when_subscribeAsyncCalled() {
        EventBus result = bus.subscribeAsync(UserCreatedEvent.class, e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_supportFluentChain_with_subscribeAsyncAndPublish() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        bus.subscribeAsync(UserCreatedEvent.class, e -> {
            invoked.set(true);
            latch.countDown();
        }).publish(new UserCreatedEvent("Vinicius"));

        latch.await(5, TimeUnit.SECONDS);
        assertTrue(invoked.get());
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_eventTypeIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAsync(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAsync(UserCreatedEvent.class, (EventListener<UserCreatedEvent>) null));
    }

    @Test
    void should_throwNullPointerException_when_priorityIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAsync(UserCreatedEvent.class, null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull_for_priorityOverload() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAsync(UserCreatedEvent.class, Priority.HIGH, null));
    }
}
