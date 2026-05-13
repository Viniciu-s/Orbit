package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventInterceptor;
import com.orbit.lib.api.Priority;
import com.orbit.lib.api.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced concurrency tests for complex EventBus features.
 * Validates thread-safety of interceptors, priorities, channels, annotations, and wildcards.
 */
class OrbitEventBusConcurrencyAdvancedTest {

    private static final int THREADS = 20;
    private static final int EVENTS_PER_THREAD = 50;

    record TestEvent(int id) implements Event {
    }

    record PaymentEvent(double amount) implements Event {
    }

    record OrderEvent(String orderId) implements Event {
    }

    private ExecutorService busExecutor;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        busExecutor = Executors.newCachedThreadPool();
        bus = new OrbitEventBus(busExecutor);
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    // -------------------------------------------------------------------------
    // 1. Interceptors — must be invoked safely under concurrent publish
    // -------------------------------------------------------------------------

    @Test
    void should_invokeAllInterceptors_when_publishingConcurrently() throws Exception {
        int interceptorCount = 5;
        AtomicInteger beforeCount = new AtomicInteger();
        AtomicInteger afterCount = new AtomicInteger();
        AtomicReference<Throwable> caught = new AtomicReference<>();

        // Register multiple interceptors
        for (int i = 0; i < interceptorCount; i++) {
            bus.addInterceptor(new EventInterceptor() {
                @Override
                public void beforePublish(Event event) {
                    beforeCount.incrementAndGet();
                }

                @Override
                public void afterPublish(Event event) {
                    afterCount.incrementAndGet();
                }
            });
        }

        bus.subscribe(TestEvent.class, e -> {
            // simple listener
        });

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                        bus.publish(new TestEvent(id * 1000 + j));
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertNull(caught.get(), () -> "No exception should occur: " + caught.get());

        int expectedCalls = THREADS * EVENTS_PER_THREAD * interceptorCount;
        assertEquals(expectedCalls, beforeCount.get(),
                "All beforePublish calls must be invoked");
        assertEquals(expectedCalls, afterCount.get(),
                "All afterPublish calls must be invoked");
    }

    // -------------------------------------------------------------------------
    // 2. Priority listeners — order must be maintained under concurrent publish
    // -------------------------------------------------------------------------

    @Test
    void should_maintainPriorityOrder_when_publishingConcurrently() throws Exception {
        ConcurrentHashMap<Integer, List<String>> eventOrders = new ConcurrentHashMap<>();

        // Register listeners with different priorities
        bus.subscribe(TestEvent.class, Priority.HIGH, e -> {
            eventOrders.computeIfAbsent(e.id(), k -> new CopyOnWriteArrayList<>()).add("HIGH");
        });
        bus.subscribe(TestEvent.class, Priority.NORMAL, e -> {
            eventOrders.computeIfAbsent(e.id(), k -> new CopyOnWriteArrayList<>()).add("NORMAL");
        });
        bus.subscribe(TestEvent.class, Priority.LOW, e -> {
            eventOrders.computeIfAbsent(e.id(), k -> new CopyOnWriteArrayList<>()).add("LOW");
        });

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                        int eventId = threadId * 1000 + j;
                        bus.publish(new TestEvent(eventId));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        // Verify every event had HIGH → NORMAL → LOW order
        int totalEvents = THREADS * EVENTS_PER_THREAD;
        assertEquals(totalEvents, eventOrders.size(), "All events must have been processed");

        for (List<String> order : eventOrders.values()) {
            assertEquals(List.of("HIGH", "NORMAL", "LOW"), order,
                    "Priority order must be maintained for each event");
        }
    }

    // -------------------------------------------------------------------------
    // 3. Annotation-based registration — concurrent register() calls
    // -------------------------------------------------------------------------

    static class AnnotatedHandler {
        final List<TestEvent> received = new CopyOnWriteArrayList<>();

        @Subscribe
        public void onTest(TestEvent event) {
            received.add(event);
        }
    }

    @Test
    void should_registerAllHandlers_when_registeredConcurrently() throws Exception {
        int handlerCount = THREADS;
        List<AnnotatedHandler> handlers = new ArrayList<>();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(handlerCount);
        ExecutorService pool = Executors.newFixedThreadPool(handlerCount);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        for (int i = 0; i < handlerCount; i++) {
            AnnotatedHandler handler = new AnnotatedHandler();
            handlers.add(handler);

            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    bus.register(handler);
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertNull(caught.get(), () -> "register() must be thread-safe: " + caught.get());

        // Verify all handlers were registered by publishing an event
        bus.publish(new TestEvent(999));

        for (AnnotatedHandler handler : handlers) {
            assertEquals(1, handler.received.size(),
                    "Each registered handler must receive the event");
        }
    }

    // -------------------------------------------------------------------------
    // 4. Channels — concurrent publishes to different channels must be isolated
    // -------------------------------------------------------------------------

    @Test
    void should_isolateChannels_when_publishingConcurrently() throws Exception {
        int channelCount = 5;
        ConcurrentHashMap<String, AtomicInteger> channelCounts = new ConcurrentHashMap<>();

        // Subscribe to multiple channels
        for (int i = 0; i < channelCount; i++) {
            String channelName = "channel-" + i;
            channelCounts.put(channelName, new AtomicInteger());

            bus.channel(channelName).subscribe(TestEvent.class, e -> {
                channelCounts.get(channelName).incrementAndGet();
            });
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS * channelCount);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        // Each thread publishes to a specific channel
        for (int ch = 0; ch < channelCount; ch++) {
            final String channelName = "channel-" + ch;
            for (int t = 0; t < THREADS; t++) {
                final int eventId = t;
                pool.submit(() -> {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                            bus.channel(channelName).publish(new TestEvent(eventId * 1000 + j));
                        }
                    } catch (Exception ex) {
                        caught.set(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
        }

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertNull(caught.get(), () -> "No exception during concurrent channel publish: " + caught.get());

        // Each channel should have received exactly THREADS * EVENTS_PER_THREAD
        int expectedPerChannel = THREADS * EVENTS_PER_THREAD;
        for (int i = 0; i < channelCount; i++) {
            String channelName = "channel-" + i;
            assertEquals(expectedPerChannel, channelCounts.get(channelName).get(),
                    "Channel " + channelName + " must receive exactly its own events");
        }
    }

    // -------------------------------------------------------------------------
    // 5. Wildcard listeners — subscribeAll must work safely under concurrency
    // -------------------------------------------------------------------------

    @Test
    void should_invokeWildcardListener_when_publishingMultipleEventTypesConcurrently()
            throws Exception {
        List<Event> allEvents = new CopyOnWriteArrayList<>();
        AtomicInteger paymentCount = new AtomicInteger();
        AtomicInteger orderCount = new AtomicInteger();

        bus.subscribeAll(e -> {
            allEvents.add(e);
            if (e instanceof PaymentEvent) {
                paymentCount.incrementAndGet();
            } else if (e instanceof OrderEvent) {
                orderCount.incrementAndGet();
            }
        });

        int half = THREADS / 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicReference<Throwable> caught = new AtomicReference<>();

        // Half threads publish PaymentEvent
        for (int i = 0; i < half; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                        bus.publish(new PaymentEvent(id * 10.0 + j));
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        // Other half publish OrderEvent
        for (int i = half; i < THREADS; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                        bus.publish(new OrderEvent("O-" + id + "-" + j));
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertNull(caught.get(), () -> "No exception during wildcard concurrent publish: " + caught.get());

        int expectedTotal = THREADS * EVENTS_PER_THREAD;
        int expectedPerType = half * EVENTS_PER_THREAD;

        assertEquals(expectedTotal, allEvents.size(),
                "Wildcard listener must receive all events");
        assertEquals(expectedPerType, paymentCount.get(),
                "Must receive all PaymentEvent instances");
        assertEquals(expectedPerType, orderCount.get(),
                "Must receive all OrderEvent instances");
    }
}
