package io.orbitbus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusSubscribeTest {

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
    void should_returnSameBusInstance_when_subscribing() {
        EventBus result = bus.subscribe(UserCreatedEvent.class, event -> {
        });
        assertSame(bus, result, "subscribe() must return the same EventBus for fluent chaining");
    }

    @Test
    void should_acceptLambdaAsListener() {
        EventListener<UserCreatedEvent> listener = event -> {
        };
        assertNotNull(listener);
        bus.subscribe(UserCreatedEvent.class, listener);
    }

    @Test
    void should_allowMultipleListenersForSameEventType() {
        List<String> calls = new ArrayList<>();
        bus.subscribe(UserCreatedEvent.class, e -> calls.add("listener1"));
        bus.subscribe(UserCreatedEvent.class, e -> calls.add("listener2"));
        bus.subscribe(UserCreatedEvent.class, e -> calls.add("listener3"));

        assertEquals(3, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_allowListenersForDifferentEventTypes() {
        bus.subscribe(UserCreatedEvent.class, e -> {
        });
        bus.subscribe(PaymentApprovedEvent.class, e -> {
        });

        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));
        assertEquals(1, bus.listenerCount(PaymentApprovedEvent.class));
    }

    @Test
    void should_startWithZeroListeners_when_noSubscriptions() {
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_eventTypeIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribe(null, event -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribe(UserCreatedEvent.class, null));
    }

    // --- Fluent chaining ---

    @Test
    void should_supportFluentChaining() {
        bus.subscribe(UserCreatedEvent.class, e -> {
        })
                .subscribe(UserCreatedEvent.class, e -> {
                });

        assertEquals(2, bus.listenerCount(UserCreatedEvent.class));
    }

    // --- Thread safety ---

    @Test
    void should_registerListenersConcurrently_without_dataRace() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                bus.subscribe(UserCreatedEvent.class, e -> {
                });
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, bus.listenerCount(UserCreatedEvent.class));
    }
}
