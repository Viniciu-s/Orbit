package io.orbitbus.core;

import io.orbitbus.annotation.Priority;
import io.orbitbus.annotation.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusAnnotationTest {

    record UserCreatedEvent(String name) implements Event {
    }

    record PaymentEvent(double amount) implements Event {
    }

    // ---- Handler fixtures -----------------------------------------------

    public static class SingleHandler {
        private final List<String> sink;

        SingleHandler(List<String> sink) {
            this.sink = sink;
        }

        @Subscribe
        public void onUser(UserCreatedEvent event) {
            sink.add(event.name());
        }
    }

    public static class MultiEventHandler {
        private final List<String> sink;

        MultiEventHandler(List<String> sink) {
            this.sink = sink;
        }

        @Subscribe
        public void onUser(UserCreatedEvent event) {
            sink.add("user:" + event.name());
        }

        @Subscribe
        public void onPayment(PaymentEvent event) {
            sink.add("payment:" + event.amount());
        }
    }

    public static class HighPriorityHandler {
        private final List<String> sink;

        HighPriorityHandler(List<String> sink) {
            this.sink = sink;
        }

        @Subscribe(priority = Priority.HIGH)
        public void onUser(UserCreatedEvent event) {
            sink.add("HIGH");
        }
    }

    public static class LowPriorityHandler {
        private final List<String> sink;

        LowPriorityHandler(List<String> sink) {
            this.sink = sink;
        }

        @Subscribe(priority = Priority.LOW)
        public void onUser(UserCreatedEvent event) {
            sink.add("LOW");
        }
    }

    public static class ThrowingHandler {
        @Subscribe
        public void onUser(UserCreatedEvent event) {
            throw new RuntimeException("handler boom");
        }
    }

    public static class NoAnnotationHandler {
        public void onUser(UserCreatedEvent event) {
        }
    }

    public static class NoParamHandler {
        @Subscribe
        public void handle() {
        }
    }

    public static class TwoParamHandler {
        @Subscribe
        public void handle(UserCreatedEvent e1, UserCreatedEvent e2) {
        }
    }

    public static class NonEventParamHandler {
        @Subscribe
        public void handle(String s) {
        }
    }

    // ---- Setup -------------------------------------------------------------

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new OrbitEventBus();
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    // ---- Happy path --------------------------------------------------------

    @Test
    void should_invokeAnnotatedMethod_when_eventPublished() {
        List<String> received = new ArrayList<>();
        bus.register(new SingleHandler(received));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), received);
    }

    @Test
    void should_invokeMultipleAnnotatedMethods_in_sameHandler() {
        List<String> received = new ArrayList<>();
        bus.register(new MultiEventHandler(received));

        bus.publish(new UserCreatedEvent("Vinicius"));
        bus.publish(new PaymentEvent(99.9));

        assertTrue(received.contains("user:Vinicius"));
        assertTrue(received.contains("payment:99.9"));
    }

    @Test
    void should_invokeMultipleHandlers_for_sameEventType() {
        List<String> sink1 = new ArrayList<>();
        List<String> sink2 = new ArrayList<>();

        bus.register(new SingleHandler(sink1));
        bus.register(new SingleHandler(sink2));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), sink1);
        assertEquals(List.of("Vinicius"), sink2);
    }

    @Test
    void should_countListeners_when_handlerRegistered() {
        bus.register(new MultiEventHandler(new ArrayList<>()));

        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));
        assertEquals(1, bus.listenerCount(PaymentEvent.class));
    }

    // ---- Priority ----------------------------------------------------------

    @Test
    void should_respectPriority_when_subscribeAnnotationHasPriority() {
        List<String> order = new CopyOnWriteArrayList<>();

        bus.register(new LowPriorityHandler(order));
        bus.register(new HighPriorityHandler(order));
        bus.subscribe(UserCreatedEvent.class, e -> order.add("NORMAL"));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("HIGH", "NORMAL", "LOW"), order);
    }

    // ---- Unregister --------------------------------------------------------

    @Test
    void should_removeAllListeners_when_unregisterCalled() {
        List<String> received = new ArrayList<>();
        MultiEventHandler handler = new MultiEventHandler(received);

        bus.register(handler);
        assertEquals(1, bus.listenerCount(UserCreatedEvent.class));
        assertEquals(1, bus.listenerCount(PaymentEvent.class));

        bus.unregister(handler);
        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
        assertEquals(0, bus.listenerCount(PaymentEvent.class));
    }

    @Test
    void should_notFireListener_after_handlerUnregistered() {
        List<String> received = new ArrayList<>();
        SingleHandler handler = new SingleHandler(received);

        bus.register(handler);
        bus.unregister(handler);
        bus.publish(new UserCreatedEvent("Vinicius"));

        assertTrue(received.isEmpty());
    }

    @Test
    void should_unregisterByIdentity_even_if_equalsOverridden() {
        // Two distinct instances with same state — unregister should only affect the registered one
        List<String> sink1 = new ArrayList<>();
        List<String> sink2 = new ArrayList<>();
        SingleHandler h1 = new SingleHandler(sink1);
        SingleHandler h2 = new SingleHandler(sink2);

        bus.register(h1);
        bus.register(h2);
        bus.unregister(h1);

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertTrue(sink1.isEmpty(), "h1 must be unregistered");
        assertEquals(List.of("Vinicius"), sink2, "h2 must still be registered");
    }

    @Test
    void should_beIdempotent_when_unregisterCalledTwice() {
        SingleHandler handler = new SingleHandler(new ArrayList<>());

        bus.register(handler);
        bus.unregister(handler);
        bus.unregister(handler); // must not throw

        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    // ---- Error isolation ---------------------------------------------------

    @Test
    void should_notAffectOtherListeners_when_annotatedMethodThrows() {
        List<String> received = new ArrayList<>();

        bus.register(new ThrowingHandler());
        bus.subscribe(UserCreatedEvent.class, e -> received.add(e.name()));

        bus.publish(new UserCreatedEvent("Vinicius"));

        assertEquals(List.of("Vinicius"), received);
    }

    // ---- No annotations — no-op --------------------------------------------

    @Test
    void should_notRegisterAnyListener_when_handlerHasNoAnnotatedMethods() {
        bus.register(new NoAnnotationHandler());

        assertEquals(0, bus.listenerCount(UserCreatedEvent.class));
    }

    // ---- Fluent ------------------------------------------------------------

    @Test
    void should_returnSameBusInstance_when_registerCalled() {
        EventBus result = bus.register(new SingleHandler(new ArrayList<>()));
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_unregisterCalled() {
        SingleHandler handler = new SingleHandler(new ArrayList<>());
        bus.register(handler);

        EventBus result = bus.unregister(handler);
        assertSame(bus, result);
    }

    // ---- Null guards -------------------------------------------------------

    @Test
    void should_throwNullPointerException_when_registerReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.register(null));
    }

    @Test
    void should_throwNullPointerException_when_unregisterReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.unregister(null));
    }

    // ---- Invalid method signatures -----------------------------------------

    @Test
    void should_throwIllegalArgumentException_when_methodHasNoParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.register(new NoParamHandler()));
    }

    @Test
    void should_throwIllegalArgumentException_when_methodHasTwoParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.register(new TwoParamHandler()));
    }

    @Test
    void should_throwIllegalArgumentException_when_parameterIsNotEvent() {
        assertThrows(IllegalArgumentException.class,
                () -> bus.register(new NonEventParamHandler()));
    }
}
