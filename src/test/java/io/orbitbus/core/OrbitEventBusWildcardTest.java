package io.orbitbus.core;

import io.orbitbus.annotation.Priority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusWildcardTest {

    record UserEvent(String name) implements Event {
    }

    record PaymentEvent(double amount) implements Event {
    }

    record OrderEvent(String id) implements Event {
    }

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new OrbitEventBus();
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    // ---- Basic wildcard behavior ------------------------------------------

    @Test
    void should_invokeWildcardListener_forAnyEventType() {
        List<Event> captured = new ArrayList<>();
        bus.subscribeAll(captured::add);

        bus.publish(new UserEvent("Alice"));
        bus.publish(new PaymentEvent(100.0));
        bus.publish(new OrderEvent("O1"));

        assertEquals(3, captured.size());
        assertTrue(captured.get(0) instanceof UserEvent);
        assertTrue(captured.get(1) instanceof PaymentEvent);
        assertTrue(captured.get(2) instanceof OrderEvent);
    }

    @Test
    void should_invokeWildcardListener_whenNoSpecificListenersRegistered() {
        List<Event> captured = new ArrayList<>();
        bus.subscribeAll(captured::add);

        bus.publish(new UserEvent("Bob"));

        assertEquals(1, captured.size());
    }

    @Test
    void should_invokeWildcard_and_specificListeners() {
        List<String> order = new ArrayList<>();
        bus.subscribeAll(e -> order.add("wildcard"));
        bus.subscribe(UserEvent.class, e -> order.add("specific"));

        bus.publish(new UserEvent("Charlie"));

        assertTrue(order.contains("wildcard"));
        assertTrue(order.contains("specific"));
        assertEquals(2, order.size());
    }

    // ---- Priority ----------------------------------------------------------

    @Test
    void should_respectPriority_between_wildcardAndSpecific() {
        List<String> order = new ArrayList<>();
        bus.subscribeAll(Priority.LOW, e -> order.add("wildcard-low"));
        bus.subscribe(UserEvent.class, Priority.HIGH, e -> order.add("specific-high"));
        bus.subscribe(UserEvent.class, Priority.NORMAL, e -> order.add("specific-normal"));

        bus.publish(new UserEvent("Dave"));

        assertEquals(List.of("specific-high", "specific-normal", "wildcard-low"), order);
    }

    @Test
    void should_respectPriority_among_wildcardListeners() {
        List<String> order = new ArrayList<>();
        bus.subscribeAll(Priority.HIGH, e -> order.add("wildcard-high"));
        bus.subscribeAll(Priority.NORMAL, e -> order.add("wildcard-normal"));
        bus.subscribeAll(Priority.LOW, e -> order.add("wildcard-low"));

        bus.publish(new UserEvent("Eve"));

        assertEquals(List.of("wildcard-high", "wildcard-normal", "wildcard-low"), order);
    }

    @Test
    void should_useNormalPriority_when_subscribeAllWithoutPriority() {
        List<String> order = new ArrayList<>();
        bus.subscribeAll(Priority.HIGH, e -> order.add("high"));
        bus.subscribeAll(e -> order.add("default"));
        bus.subscribeAll(Priority.LOW, e -> order.add("low"));

        bus.publish(new UserEvent("Frank"));

        assertEquals(List.of("high", "default", "low"), order);
    }

    // ---- Unsubscribe -------------------------------------------------------

    @Test
    void should_notInvokeWildcardListener_after_unsubscribeAll() {
        List<Event> captured = new ArrayList<>();
        EventListener<Event> listener = captured::add;
        bus.subscribeAll(listener);
        bus.unsubscribeAll(listener);

        bus.publish(new UserEvent("Grace"));

        assertTrue(captured.isEmpty());
    }

    @Test
    void should_notRemoveSpecificListeners_when_unsubscribeAllCalled() {
        List<String> invoked = new ArrayList<>();
        EventListener<Event> wildcardListener = e -> invoked.add("wildcard");
        EventListener<Event> otherWildcard = e -> {
        };
        bus.subscribeAll(wildcardListener);
        bus.subscribe(UserEvent.class, e -> invoked.add("specific"));

        bus.unsubscribeAll(otherWildcard);
        bus.publish(new UserEvent("Heidi"));

        assertEquals(List.of("wildcard", "specific"), invoked);
    }

    @Test
    void should_beIdempotent_when_unsubscribeAll_calledForNonRegisteredListener() {
        assertDoesNotThrow(() -> bus.unsubscribeAll(e -> {
        }));
    }

    // ---- Async -------------------------------------------------------------

    @Test
    void should_invokeWildcardListenerAsync_when_subscribeAllAsync() throws Exception {
        List<Event> captured = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribeAllAsync(e -> {
            captured.add(e);
            latch.countDown();
        });

        bus.publish(new UserEvent("Ivan"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertEquals(1, captured.size());
    }

    @Test
    void should_invokeWildcardListenerAsync_with_publishAsync() throws Exception {
        List<Event> captured = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribeAllAsync(e -> {
            captured.add(e);
            latch.countDown();
        });

        bus.publishAsync(new UserEvent("Judy")).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, captured.size());
    }

    // ---- Fluent API --------------------------------------------------------

    @Test
    void should_returnSameBusInstance_when_subscribeAllCalled() {
        EventBus result = bus.subscribeAll(e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_subscribeAllWithPriorityCalled() {
        EventBus result = bus.subscribeAll(Priority.HIGH, e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_subscribeAllAsyncCalled() {
        EventBus result = bus.subscribeAllAsync(e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_subscribeAllAsyncWithPriorityCalled() {
        EventBus result = bus.subscribeAllAsync(Priority.LOW, e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_unsubscribeAllCalled() {
        EventBus result = bus.unsubscribeAll(e -> {
        });
        assertSame(bus, result);
    }

    // ---- NPE guards --------------------------------------------------------

    @Test
    void should_throwNullPointerException_when_subscribeAllReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.subscribeAll(null));
    }

    @Test
    void should_throwNullPointerException_when_subscribeAllWithPriorityReceivesNullPriority() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAll(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_subscribeAllWithPriorityReceivesNullListener() {
        assertThrows(NullPointerException.class, () -> bus.subscribeAll(Priority.HIGH, null));
    }

    @Test
    void should_throwNullPointerException_when_subscribeAllAsyncReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.subscribeAllAsync(null));
    }

    @Test
    void should_throwNullPointerException_when_subscribeAllAsyncWithPriorityReceivesNullPriority() {
        assertThrows(NullPointerException.class,
                () -> bus.subscribeAllAsync(null, e -> {
                }));
    }

    @Test
    void should_throwNullPointerException_when_subscribeAllAsyncWithPriorityReceivesNullListener() {
        assertThrows(NullPointerException.class, () -> bus.subscribeAllAsync(Priority.LOW, null));
    }

    @Test
    void should_throwNullPointerException_when_unsubscribeAllReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.unsubscribeAll(null));
    }

    // ---- Error isolation ---------------------------------------------------

    @Test
    void should_notInterruptOtherListeners_when_wildcardListenerThrows() {
        List<String> invoked = new ArrayList<>();
        bus.subscribeAll(e -> {
            throw new RuntimeException("wildcard boom");
        });
        bus.subscribe(UserEvent.class, e -> invoked.add("specific"));

        assertDoesNotThrow(() -> bus.publish(new UserEvent("Karen")));

        assertEquals(List.of("specific"), invoked);
    }

    // ---- Integration -------------------------------------------------------

    @Test
    void should_work_with_multipleEventTypes() {
        List<String> log = new ArrayList<>();
        bus.subscribeAll(e -> log.add("all:" + e.getClass().getSimpleName()));
        bus.subscribe(UserEvent.class, e -> log.add("user:" + e.name()));
        bus.subscribe(PaymentEvent.class, e -> log.add("payment:" + e.amount()));

        bus.publish(new UserEvent("Leo"));
        bus.publish(new PaymentEvent(50.0));

        assertEquals(4, log.size());
        assertTrue(log.contains("all:UserEvent"));
        assertTrue(log.contains("user:Leo"));
        assertTrue(log.contains("all:PaymentEvent"));
        assertTrue(log.contains("payment:50.0"));
    }
}
