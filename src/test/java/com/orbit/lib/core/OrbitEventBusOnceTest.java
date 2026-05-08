package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrbitEventBusOnceTest {

    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentApprovedEvent(double amount) implements Event {
    }

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new OrbitEventBus();
    }

    // --- Happy path ---

    @Test
    void should_invokeListener_on_firstPublish() {
        AtomicInteger count = new AtomicInteger();
        bus.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, count.get());
    }

    @Test
    void should_notInvokeListener_on_secondPublish() {
        AtomicInteger count = new AtomicInteger();
        bus.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, count.get(), "once() listener must fire exactly once");
    }

    @Test
    void should_removeListenerFromRegistry_after_firstPublish() {
        bus.once(UserCreatedEvent.class, e -> {
        });
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));

        bus.publish(new UserCreatedEvent("Vinicius"));
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_notFireAtAll_when_noPublishOccurs() {
        AtomicInteger count = new AtomicInteger();
        bus.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        assertEquals(0, count.get());
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_notAffectRegularSubscribers_when_onceListenerFires() {
        AtomicInteger onceCount = new AtomicInteger();
        AtomicInteger regularCount = new AtomicInteger();

        bus.once(UserCreatedEvent.class, e -> onceCount.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> regularCount.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, onceCount.get(), "once() fires once");
        assertEquals(2, regularCount.get(), "regular listener fires every time");
    }

    @Test
    void should_allowMultipleOnceListeners_each_firingOnce() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        bus.once(UserCreatedEvent.class, e -> count1.incrementAndGet());
        bus.once(UserCreatedEvent.class, e -> count2.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void should_notAffectOtherEventTypes() {
        AtomicInteger userCount = new AtomicInteger();

        bus.once(UserCreatedEvent.class, e -> userCount.incrementAndGet());
        bus.subscribe(PaymentApprovedEvent.class, e -> {
        });

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, userCount.get());
        assertEquals(1, bus.listenerCount(PaymentApprovedEvent.class));
    }

    // --- Fluent ---

    @Test
    void should_returnSameBusInstance_when_onceCalled() {
        EventBus result = bus.once(UserCreatedEvent.class, e -> {
        });
        assertSame(bus, result, "once() must return the same EventBus for fluent chaining");
    }

    @Test
    void should_supportFluentChain_with_onceAndSubscribe() {
        AtomicInteger onceCount = new AtomicInteger();
        AtomicInteger subCount = new AtomicInteger();

        bus.once(UserCreatedEvent.class, e -> onceCount.incrementAndGet())
                .subscribe(UserCreatedEvent.class, e -> subCount.incrementAndGet())
                .publish(new UserCreatedEvent("Vinicius"))
                .publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, onceCount.get());
        assertEquals(2, subCount.get());
    }

    // --- Thread safety ---

    @Test
    void should_fireExactlyOnce_when_publishedConcurrently() throws InterruptedException {
        int threadCount = 50;
        AtomicInteger count = new AtomicInteger();
        bus.once(UserCreatedEvent.class, e -> count.incrementAndGet());

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                bus.publish(new UserCreatedEvent("Vinicius"));
                done.countDown();
            });
        }

        ready.await();
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, count.get(), "once() must fire exactly once under concurrent publish");
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_eventTypeIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.once(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.once(UserCreatedEvent.class, null));
    }
}
