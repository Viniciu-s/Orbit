package io.orbitbus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTest {

    // Concrete event used only within tests
    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentApprovedEvent(double amount) implements Event {
    }

    @Test
    void should_beAnInterface() {
        assertTrue(Event.class.isInterface(), "Event must be an interface");
    }

    @Test
    void should_beAssignableFromConcreteEvent() {
        Event event = new UserCreatedEvent("Vinicius");
        assertInstanceOf(Event.class, event);
    }

    @Test
    void should_allowMultipleConcreteImplementations() {
        Event userEvent = new UserCreatedEvent("Vinicius");
        Event paymentEvent = new PaymentApprovedEvent(99.90);

        assertInstanceOf(Event.class, userEvent);
        assertInstanceOf(Event.class, paymentEvent);
    }

    @Test
    void should_allowEventAsTypeToken() {
        Class<UserCreatedEvent> token = UserCreatedEvent.class;
        assertTrue(Event.class.isAssignableFrom(token),
                "Class token of a concrete event must be assignable to Event");
    }

    @Test
    void should_preserveEventData() {
        UserCreatedEvent event = new UserCreatedEvent("Vinicius");
        assertTrue(event instanceof Event);
        assertTrue(event.name().equals("Vinicius"), "Event data must be preserved");
    }
}
