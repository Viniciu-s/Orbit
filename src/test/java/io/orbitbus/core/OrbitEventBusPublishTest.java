package io.orbitbus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusPublishTest {

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
    void should_invokeListener_when_matchingEventPublished() {
        List<String> received = new ArrayList<>();
        bus.subscribe(UserCreatedEvent.class, e -> received.add(e.name()));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), received);
    }

    @Test
    void should_invokeAllListeners_when_multipleSubscribed() {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(3, count.get());
    }

    @Test
    void should_notInvokeListeners_of_otherEventTypes() {
        AtomicInteger userCount = new AtomicInteger();
        AtomicInteger paymentCount = new AtomicInteger();

        bus.subscribe(UserCreatedEvent.class, e -> userCount.incrementAndGet());
        bus.subscribe(PaymentApprovedEvent.class, e -> paymentCount.incrementAndGet());

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, userCount.get());
        assertEquals(0, paymentCount.get());
    }

    @Test
    void should_doNothing_when_noListenersRegistered() {
        // must not throw
        bus.publish(new UserCreatedEvent("Vinicius"));
    }

    @Test
    void should_returnSameBusInstance_when_publishing() {
        EventBus result = bus.publish(new UserCreatedEvent("Vinicius"));
        assertSame(bus, result, "publish() must return the same EventBus for fluent chaining");
    }

    @Test
    void should_supportFluentChaining_subscribeAndPublish() {
        List<String> received = new ArrayList<>();

        bus.subscribe(UserCreatedEvent.class, e -> received.add(e.name()))
                .publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), received);
    }

    @Test
    void should_deliverEventDataIntact_to_listener() {
        List<UserCreatedEvent> received = new ArrayList<>();
        bus.subscribe(UserCreatedEvent.class, received::add);

        UserCreatedEvent event = new UserCreatedEvent("Vinicius");
        bus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    // --- Null guard ---

    @Test
    void should_throwNullPointerException_when_publishingNullEvent() {
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }

    // --- Error isolation ---

    @Test
    void should_continueInvokingRemainingListeners_when_oneThrows() {
        AtomicInteger count = new AtomicInteger();

        bus.subscribe(UserCreatedEvent.class, e -> {
            throw new RuntimeException("intentional failure");
        });
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());

        // must not throw despite the failing listener
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(2, count.get(), "Remaining listeners must still execute after one fails");
    }

    @Test
    void should_notPropagateListenerException_to_caller() {
        bus.subscribe(UserCreatedEvent.class, e -> {
            throw new RuntimeException("intentional failure");
        });

        // caller must never see the exception
        bus.publish(new UserCreatedEvent("Vinicius"));
    }

    @Test
    void should_isolateEachListenerFailure_independently() {
        List<String> executed = Collections.synchronizedList(new ArrayList<>());

        bus.subscribe(UserCreatedEvent.class, e -> {
            throw new RuntimeException("listener 1 fails");
        });
        bus.subscribe(UserCreatedEvent.class, e -> executed.add("listener2"));
        bus.subscribe(UserCreatedEvent.class, e -> {
            throw new RuntimeException("listener 3 fails");
        });
        bus.subscribe(UserCreatedEvent.class, e -> executed.add("listener4"));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("listener2", "listener4"), executed);
    }

    // --- Thread safety ---

    @Test
    void should_deliverEventToAllListeners_when_publishedConcurrently()
            throws InterruptedException {
        int threadCount = 20;
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(UserCreatedEvent.class, e -> count.incrementAndGet());

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                bus.publish(new UserCreatedEvent("Vinicius"));
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, count.get());
    }
}
