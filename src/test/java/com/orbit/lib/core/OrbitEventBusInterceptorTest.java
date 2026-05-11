package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusInterceptorTest {

    record TestEvent(String value) implements Event {
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

    // ---- beforePublish -----------------------------------------------------

    @Test
    void should_callBeforePublish_beforeListeners() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("before");
            }

            @Override
            public void afterPublish(Event event) {
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("before", "listener"), order);
    }

    @Test
    void should_callMultipleBeforePublish_inRegistrationOrder() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("intercept1");
            }

            @Override
            public void afterPublish(Event event) {
            }
        });
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("intercept2");
            }

            @Override
            public void afterPublish(Event event) {
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("intercept1", "intercept2", "listener"), order);
    }

    @Test
    void should_abortPublish_when_beforePublishThrows() {
        List<String> invoked = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                throw new RuntimeException("validation failed");
            }

            @Override
            public void afterPublish(Event event) {
                invoked.add("after");
            }
        });
        bus.subscribe(TestEvent.class, e -> invoked.add("listener"));

        assertThrows(RuntimeException.class, () -> bus.publish(new TestEvent("x")));

        assertTrue(invoked.isEmpty());
    }

    // ---- afterPublish ------------------------------------------------------

    @Test
    void should_callAfterPublish_afterListeners() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after");
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("listener", "after"), order);
    }

    @Test
    void should_callMultipleAfterPublish_inRegistrationOrder() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after1");
            }
        });
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after2");
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("listener", "after1", "after2"), order);
    }

    @Test
    void should_isolateAfterPublishException_andNotInterruptOtherInterceptors() {
        List<String> order = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        bus.setErrorHandler(error::set);

        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after1");
                throw new RuntimeException("after1 boom");
            }
        });
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after2");
            }
        });

        assertDoesNotThrow(() -> bus.publish(new TestEvent("x")));

        assertEquals(List.of("after1", "after2"), order);
        assertTrue(error.get().getMessage().contains("after1 boom"));
    }

    // ---- Full lifecycle ----------------------------------------------------

    @Test
    void should_callFullLifecycle_inCorrectOrder() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("before");
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after");
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener1"));
        bus.subscribe(TestEvent.class, e -> order.add("listener2"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("before", "listener1", "listener2", "after"), order);
    }

    // ---- Remove interceptor ------------------------------------------------

    @Test
    void should_notCallInterceptor_when_removed() {
        List<String> invoked = new ArrayList<>();
        EventInterceptor interceptor = new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                invoked.add("before");
            }

            @Override
            public void afterPublish(Event event) {
                invoked.add("after");
            }
        };

        bus.addInterceptor(interceptor);
        bus.removeInterceptor(interceptor);
        bus.subscribe(TestEvent.class, e -> invoked.add("listener"));

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("listener"), invoked);
    }

    @Test
    void should_beIdempotent_when_removingInterceptorNotRegistered() {
        EventInterceptor interceptor = new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
            }
        };

        assertDoesNotThrow(() -> bus.removeInterceptor(interceptor));
    }

    // ---- Fluent API --------------------------------------------------------

    @Test
    void should_returnSameBusInstance_when_addInterceptorCalled() {
        EventInterceptor interceptor = new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
            }
        };

        EventBus result = bus.addInterceptor(interceptor);
        assertSame(bus, result);
    }

    @Test
    void should_returnSameBusInstance_when_removeInterceptorCalled() {
        EventInterceptor interceptor = new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
            }
        };

        EventBus result = bus.removeInterceptor(interceptor);
        assertSame(bus, result);
    }

    // ---- NPE guards --------------------------------------------------------

    @Test
    void should_throwNullPointerException_when_addInterceptorReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.addInterceptor(null));
    }

    @Test
    void should_throwNullPointerException_when_removeInterceptorReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.removeInterceptor(null));
    }

    // ---- publishAsync ------------------------------------------------------

    @Test
    void should_callInterceptors_when_publishAsync() throws Exception {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("before");
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after");
            }
        });
        bus.subscribe(TestEvent.class, e -> order.add("listener"));

        CompletableFuture<Void> future = bus.publishAsync(new TestEvent("x"));
        future.get(2, TimeUnit.SECONDS);

        assertEquals(List.of("before", "listener", "after"), order);
    }

    // ---- Event inspection --------------------------------------------------

    @Test
    void should_receiveCorrectEvent_inBeforePublish() {
        AtomicReference<Event> captured = new AtomicReference<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                captured.set(event);
            }

            @Override
            public void afterPublish(Event event) {
            }
        });

        TestEvent published = new TestEvent("hello");
        bus.publish(published);

        assertSame(published, captured.get());
    }

    @Test
    void should_receiveCorrectEvent_inAfterPublish() {
        AtomicReference<Event> captured = new AtomicReference<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
            }

            @Override
            public void afterPublish(Event event) {
                captured.set(event);
            }
        });

        TestEvent published = new TestEvent("hello");
        bus.publish(published);

        assertSame(published, captured.get());
    }

    // ---- No listeners case -------------------------------------------------

    @Test
    void should_callInterceptors_evenWhenNoListenersRegistered() {
        List<String> order = new ArrayList<>();
        bus.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                order.add("before");
            }

            @Override
            public void afterPublish(Event event) {
                order.add("after");
            }
        });

        bus.publish(new TestEvent("x"));

        assertEquals(List.of("before", "after"), order);
    }
}
