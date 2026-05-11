package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusErrorHandlerTest {

    record TestEvent(String value) implements Event {
    }

    static class ThrowingAnnotatedHandler {
        @Subscribe
        public void onEvent(TestEvent event) {
            throw new RuntimeException("annotated boom");
        }
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

    // ---- Happy path --------------------------------------------------------

    @Test
    void should_callErrorHandler_when_listenerThrows() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        bus.setErrorHandler(captured::set);

        RuntimeException boom = new RuntimeException("boom");
        bus.subscribe(TestEvent.class, e -> {
            throw boom;
        });

        bus.publish(new TestEvent("x"));

        assertSame(boom, captured.get());
    }

    @Test
    void should_notCallErrorHandler_when_listenerSucceeds() {
        AtomicInteger count = new AtomicInteger();
        bus.setErrorHandler(e -> count.incrementAndGet());
        bus.subscribe(TestEvent.class, e -> {
        });

        bus.publish(new TestEvent("x"));

        assertEquals(0, count.get());
    }

    @Test
    void should_notInterruptOtherListeners_when_errorHandlerIsSet() {
        List<String> received = new ArrayList<>();
        bus.setErrorHandler(e -> {
        });
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("boom");
        });
        bus.subscribe(TestEvent.class, e -> received.add(e.value()));

        bus.publish(new TestEvent("hello"));

        assertEquals(List.of("hello"), received);
    }

    @Test
    void should_callErrorHandler_forEachFailingListener() {
        AtomicInteger count = new AtomicInteger();
        bus.setErrorHandler(e -> count.incrementAndGet());
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("1");
        });
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("2");
        });

        bus.publish(new TestEvent("x"));

        assertEquals(2, count.get());
    }

    // ---- Fluent & NPE guards -----------------------------------------------

    @Test
    void should_returnSameBusInstance_when_setErrorHandlerCalled() {
        EventBus result = bus.setErrorHandler(e -> {
        });
        assertSame(bus, result);
    }

    @Test
    void should_throwNullPointerException_when_setErrorHandlerReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.setErrorHandler(null));
    }

    // ---- Replace handler ---------------------------------------------------

    @Test
    void should_useLatestErrorHandler_when_setErrorHandlerCalledMultipleTimes() {
        List<Throwable> first = new ArrayList<>();
        List<Throwable> second = new ArrayList<>();
        bus.setErrorHandler(first::add);
        bus.setErrorHandler(second::add);
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("boom");
        });

        bus.publish(new TestEvent("x"));

        assertTrue(first.isEmpty());
        assertEquals(1, second.size());
    }

    // ---- Annotation-based listeners ----------------------------------------

    @Test
    void should_callErrorHandler_when_annotatedListenerThrows() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        bus.setErrorHandler(captured::set);
        bus.register(new ThrowingAnnotatedHandler());

        bus.publish(new TestEvent("x"));

        assertNotNull(captured.get());
        // MethodListener wraps the original in RuntimeException
        assertNotNull(captured.get().getCause());
        assertEquals("annotated boom", captured.get().getCause().getMessage());
    }

    // ---- Async publish path ------------------------------------------------

    @Test
    void should_callErrorHandler_when_publishAsyncListenerThrows() throws Exception {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        bus.setErrorHandler(e -> {
            captured.set(e);
            latch.countDown();
        });

        RuntimeException boom = new RuntimeException("async boom");
        bus.subscribe(TestEvent.class, e -> {
            throw boom;
        });

        bus.publishAsync(new TestEvent("x")).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(boom, captured.get());
    }

    // ---- Fault isolation ---------------------------------------------------

    @Test
    void should_notPropagateException_when_errorHandlerItselfThrows() {
        bus.setErrorHandler(e -> {
            throw new RuntimeException("handler boom");
        });
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("listener boom");
        });

        assertDoesNotThrow(() -> bus.publish(new TestEvent("x")));
    }

    @Test
    void should_notThrow_when_noCustomErrorHandlerSetAndListenerFails() {
        bus.subscribe(TestEvent.class, e -> {
            throw new RuntimeException("boom");
        });

        assertDoesNotThrow(() -> bus.publish(new TestEvent("x")));
    }
}
