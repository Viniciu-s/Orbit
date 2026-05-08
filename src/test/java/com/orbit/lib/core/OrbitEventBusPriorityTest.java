package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrbitEventBusPriorityTest {

    record TestEvent(String value) implements Event {
    }

    record OtherEvent() implements Event {
    }

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new OrbitEventBus();
    }

    // --- Priority ordering ---

    @Test
    void should_invokeListeners_in_priority_order() {
        List<String> order = new ArrayList<>();

        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW"));
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH"));
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add("NORMAL"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("HIGH", "NORMAL", "LOW"), order);
    }

    @Test
    void should_preserveRegistrationOrder_within_same_priority() {
        List<Integer> order = new ArrayList<>();

        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add(1));
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add(2));
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add(3));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void should_treatDefaultSubscribeAsNormal() {
        List<String> order = new ArrayList<>();

        bus.subscribe(TestEvent.class, e -> order.add("DEFAULT"));
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH"));
        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("HIGH", "DEFAULT", "LOW"), order);
    }

    @Test
    void should_correctlyOrder_allThreePriorities_whenRegisteredInReverseOrder() {
        List<String> order = new ArrayList<>();

        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW1"));
        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW2"));
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add("NORMAL1"));
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH1"));
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH2"));
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> order.add("NORMAL2"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("HIGH1", "HIGH2", "NORMAL1", "NORMAL2", "LOW1", "LOW2"), order);
    }

    @Test
    void should_notAffectOtherEventTypes_when_prioritySubscribed() {
        List<String> order = new ArrayList<>();

        bus.subscribe(OtherEvent.class, Priority.HIGH, e -> order.add("OTHER_HIGH"));
        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW"));
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("HIGH", "LOW"), order);
    }

    // --- Fluent ---

    @Test
    void should_returnSameBusInstance_when_prioritySubscribeCalled() {
        EventBus result = bus.subscribe(TestEvent.class, Priority.HIGH, e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_supportFluentChain_with_priorityAndPublish() {
        List<String> order = new ArrayList<>();

        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW"))
                .subscribe(TestEvent.class, Priority.HIGH, e -> order.add("HIGH"))
                .publish(new TestEvent("x"));

        assertEquals(List.of("HIGH", "LOW"), order);
    }

    // --- Interaction with listenerCount / unsubscribe ---

    @Test
    void should_countPrioritizedListeners() {
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> {
        });
        bus.subscribe(TestEvent.class, Priority.LOW, e -> {
        });

        assertEquals(2, bus.listenerCount(TestEvent.class));
    }

    @Test
    void should_unsubscribe_prioritizedListener() {
        List<String> order = new ArrayList<>();
        com.orbit.lib.api.EventListener<TestEvent> highListener = e -> order.add("HIGH");

        bus.subscribe(TestEvent.class, Priority.HIGH, highListener);
        bus.subscribe(TestEvent.class, Priority.LOW, e -> order.add("LOW"));
        bus.unsubscribe(TestEvent.class, highListener);

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("LOW"), order);
        assertEquals(1, bus.listenerCount(TestEvent.class));
    }

    // --- Null guards ---

    @Test
    void should_throwNullPointerException_when_eventTypeIsNull_for_prioritySubscribe() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribe(null, Priority.HIGH, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_priorityIsNull() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribe(TestEvent.class, null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_listenerIsNull_for_prioritySubscribe() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribe(TestEvent.class, Priority.HIGH, null));
    }
}
