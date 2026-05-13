package io.orbitbus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrbitEventBusUnsubscribeTest {

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
    void should_notInvokeListener_after_unsubscribe() {
        AtomicInteger count = new AtomicInteger();
        EventListener<UserCreatedEvent> listener = e -> count.incrementAndGet();

        bus.subscribe(UserCreatedEvent.class, listener);
        bus.publish(new UserCreatedEvent("Vinicius"));
        assertEquals(1, count.get());

        bus.unsubscribe(UserCreatedEvent.class, listener);
        bus.publish(new UserCreatedEvent("Vinicius"));
        assertEquals(1, count.get(), "Listener must not be called after unsubscribe");
    }

    @Test
    void should_decrementListenerCount_after_unsubscribe() {
        EventListener<UserCreatedEvent> listener = e -> {
        };

        bus.subscribe(UserCreatedEvent.class, listener);
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));

        bus.unsubscribe(UserCreatedEvent.class, listener);
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_removeOnlyTargetListener_when_multipleRegistered() {
        AtomicInteger count = new AtomicInteger();
        EventListener<UserCreatedEvent> toRemove = e -> {
        };
        EventListener<UserCreatedEvent> toKeep = e -> count.incrementAndGet();

        bus.subscribe(UserCreatedEvent.class, toRemove);
        bus.subscribe(UserCreatedEvent.class, toKeep);

        bus.unsubscribe(UserCreatedEvent.class, toRemove);
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(1, count.get(), "Remaining listener must still execute");
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_removeOnlyOneInstance_when_sameListenerRegisteredTwice() {
        AtomicInteger count = new AtomicInteger();
        EventListener<UserCreatedEvent> listener = e -> count.incrementAndGet();

        bus.subscribe(UserCreatedEvent.class, listener);
        bus.subscribe(UserCreatedEvent.class, listener);
        assertEquals(2, bus.listenerCount(UserCreatedEvent.class));

        bus.unsubscribe(UserCreatedEvent.class, listener);
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));

        bus.publish(new UserCreatedEvent("Vinicius"));
        assertEquals(1, count.get(), "One remaining registration must still fire");
    }

    @Test
    void should_notAffectOtherEventTypes_when_unsubscribing() {
        AtomicInteger userCount = new AtomicInteger();
        AtomicInteger paymentCount = new AtomicInteger();

        EventListener<UserCreatedEvent> userListener = e -> userCount.incrementAndGet();
        bus.subscribe(UserCreatedEvent.class, userListener);
        bus.subscribe(PaymentApprovedEvent.class, e -> paymentCount.incrementAndGet());

        bus.unsubscribe(UserCreatedEvent.class, userListener);

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new PaymentApprovedEvent(99.9));

        assertEquals(0, userCount.get());
        assertEquals(1, paymentCount.get());
    }

    // --- Idempotence ---

    @Test
    void should_doNothing_when_unsubscribingListenerNotRegistered() {
        EventListener<UserCreatedEvent> listener = e -> {
        };
        // must not throw
        bus.unsubscribe(UserCreatedEvent.class, listener);
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    @Test
    void should_doNothing_when_unsubscribingFromEmptyBus() {
        // must not throw
        bus.unsubscribe(UserCreatedEvent.class, e -> {
        });
    }

    // --- Fluent ---

    @Test
    void should_returnSameBusInstance_when_unsubscribing() {
        EventListener<UserCreatedEvent> listener = e -> {
        };
        bus.subscribe(UserCreatedEvent.class, listener);
        EventBus result = bus.unsubscribe(UserCreatedEvent.class, listener);
        assertSame(bus, result, "unsubscribe() must return the same EventBus for fluent chaining");
    }

    @Test
    void should_supportFluentChain_subscribeUnsubscribePublish() {
        AtomicInteger count = new AtomicInteger();
        EventListener<UserCreatedEvent> listener = e -> count.incrementAndGet();

        bus.subscribe(UserCreatedEvent.class, listener)
                .unsubscribe(UserCreatedEvent.class, listener)
                .publish(new UserCreatedEvent("Vinicius"));

        assertEquals(0, count.get());
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_eventTypeIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.unsubscribe(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.unsubscribe(UserCreatedEvent.class, null));
    }
}
