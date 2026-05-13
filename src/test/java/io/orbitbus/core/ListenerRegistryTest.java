package io.orbitbus.core;

import io.orbitbus.listener.ListenerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerRegistryTest {

    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentApprovedEvent(double amount) implements Event {
    }

    private ListenerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ListenerRegistry();
    }

    // --- Happy path ---

    @Test
    void should_returnEmptyList_when_noListenersRegistered() {
        List<EventListener<?>> result = registry.getListeners(UserCreatedEvent.class);
        assertTrue(result.isEmpty());
    }

    @Test
    void should_returnRegisteredListener_after_register() {
        EventListener<UserCreatedEvent> listener = e -> {
        };
        registry.register(UserCreatedEvent.class, listener);

        List<EventListener<?>> listeners = registry.getListeners(UserCreatedEvent.class);
        assertEquals(1, listeners.size());
    }

    @Test
    void should_returnAllListeners_when_multipleRegistered() {
        registry.register(UserCreatedEvent.class, e -> {
        });
        registry.register(UserCreatedEvent.class, e -> {
        });
        registry.register(UserCreatedEvent.class, e -> {
        });

        assertEquals(3, registry.getListeners(UserCreatedEvent.class).size());
    }

    @Test
    void should_isolateListeners_by_eventType() {
        registry.register(UserCreatedEvent.class, e -> {
        });
        registry.register(PaymentApprovedEvent.class, e -> {
        });
        registry.register(PaymentApprovedEvent.class, e -> {
        });

        assertEquals(1, registry.count(UserCreatedEvent.class));
        assertEquals(2, registry.count(PaymentApprovedEvent.class));
    }

    @Test
    void should_returnZero_when_countCalledWithNoRegistrations() {
        assertEquals(0, registry.count(UserCreatedEvent.class));
    }

    @Test
    void should_returnSnapshotList_so_iterationIsSafe() {
        registry.register(UserCreatedEvent.class, e -> {
        });
        List<EventListener<?>> first = registry.getListeners(UserCreatedEvent.class);
        registry.register(UserCreatedEvent.class, e -> {
        });
        List<EventListener<?>> second = registry.getListeners(UserCreatedEvent.class);

        // Snapshot must reflect state at call time — sizes differ
        assertNotSame(first, second);
        assertEquals(1, first.size());
        assertEquals(2, second.size());
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_registerWithNullType() {
        assertThrows(NullPointerException.class,
                () -> registry.register(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_registerWithNullListener() {
        assertThrows(NullPointerException.class,
                () -> registry.register(UserCreatedEvent.class, null));
    }

    @Test
    void should_throwNullPointerException_when_getListenersWithNullType() {
        assertThrows(NullPointerException.class,
                () -> registry.getListeners(null));
    }

    @Test
    void should_throwNullPointerException_when_countWithNullType() {
        assertThrows(NullPointerException.class,
                () -> registry.count(null));
    }

    // --- Thread safety ---

    @Test
    void should_registerConcurrently_without_dataRace() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                registry.register(UserCreatedEvent.class, e -> {
                });
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threadCount, registry.count(UserCreatedEvent.class));
    }
}
