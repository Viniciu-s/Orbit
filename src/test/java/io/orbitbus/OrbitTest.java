package io.orbitbus;

import io.orbitbus.core.Event;
import io.orbitbus.core.EventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class OrbitTest {

    record UserCreatedEvent(String name) implements Event {
    }

    // --- Factory ---

    @Test
    void should_returnNonNullEventBus_when_createCalled() {
        EventBus bus = Orbit.create();
        assertNotNull(bus);
    }

    @Test
    void should_returnNewInstance_on_eachCreateCall() {
        EventBus first = Orbit.create();
        EventBus second = Orbit.create();
        assertNotSame(first, second, "Each Orbit.create() must return a new independent instance");
    }

    // --- Full fluent chain (README example) ---

    @Test
    void should_supportFullFluentChain_subscribeAndPublish() {
        List<String> received = new ArrayList<>();

        Orbit.create()
                .subscribe(UserCreatedEvent.class, e -> received.add(e.name()))
                .publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), received);
    }

    @Test
    void should_supportMultipleSubscribes_in_fluentChain() {
        List<String> received = new ArrayList<>();

        Orbit.create()
                .subscribe(UserCreatedEvent.class, e -> received.add("listener1:" + e.name()))
                .subscribe(UserCreatedEvent.class, e -> received.add("listener2:" + e.name()))
                .publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("listener1:Vinicius", "listener2:Vinicius"), received);
    }

    @Test
    void should_supportMultiplePublishes_in_fluentChain() {
        List<String> received = new ArrayList<>();

        Orbit.create()
                .subscribe(UserCreatedEvent.class, e -> received.add(e.name()))
                .publish(new UserCreatedEvent("Alice"))
                .publish(new UserCreatedEvent("Bob"));

        assertEquals(List.of("Alice", "Bob"), received);
    }
}
