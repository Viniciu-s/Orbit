package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventBus;
import com.orbit.lib.api.EventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
 * Stress tests validating thread safety of the event bus under high concurrency.
 * All scenarios exercise real concurrent access — no Thread.sleep.
 */
class OrbitEventBusThreadSafetyTest {

    private static final int THREADS = 20;

    record TestEvent(int id) implements Event {
    }

    record OtherEvent(int id) implements Event {
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
    // 1. Concurrent subscribe — all registrations must be visible
    // -------------------------------------------------------------------------

    @Test
    void should_registerAllListeners_when_subscribedConcurrently() throws Exception {
        int perThread = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < perThread; j++) {
                        bus.subscribe(TestEvent.class, e -> {
                        });
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

        assertEquals(THREADS * perThread, bus.listenerCount(TestEvent.class));
    }

    // -------------------------------------------------------------------------
    // 2. Concurrent publish — every event must be delivered to pre-registered listener
    // -------------------------------------------------------------------------

    @Test
    void should_deliverAllEvents_when_publishedConcurrently() throws Exception {
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(TestEvent.class, e -> count.incrementAndGet());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    bus.publish(new TestEvent(id));
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

        assertEquals(THREADS, count.get());
    }

    // -------------------------------------------------------------------------
    // 3. Concurrent subscribe + publish — no ConcurrentModificationException
    // -------------------------------------------------------------------------

    @Test
    void should_notThrow_when_subscribeAndPublishConcurrently() throws Exception {
        bus.subscribe(TestEvent.class, e -> {
        });

        AtomicReference<Throwable> caught = new AtomicReference<>();
        int half = THREADS / 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < half; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < 50; j++) {
                        bus.publish(new TestEvent(id));
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < half; i++) {
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < 50; j++) {
                        bus.subscribe(TestEvent.class, e -> {
                        });
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

        assertNull(caught.get(),
                () -> "Exception during concurrent subscribe+publish: " + caught.get());
    }

    // -------------------------------------------------------------------------
    // 4. Concurrent unsubscribe + publish — no exception during dispatch
    // -------------------------------------------------------------------------

    @Test
    void should_notThrow_when_unsubscribeAndPublishConcurrently() throws Exception {
        List<EventListener<TestEvent>> listeners = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            EventListener<TestEvent> l = e -> {
            };
            bus.subscribe(TestEvent.class, l);
            listeners.add(l);
        }

        AtomicReference<Throwable> caught = new AtomicReference<>();
        int half = THREADS / 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < half; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < 50; j++) {
                        bus.publish(new TestEvent(id));
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < half; i++) {
            final int idx = i % listeners.size();
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    bus.unsubscribe(TestEvent.class, listeners.get(idx));
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

        assertNull(caught.get(),
                () -> "Exception during concurrent unsubscribe+publish: " + caught.get());
    }

    // -------------------------------------------------------------------------
    // 5. Concurrent subscribe + unsubscribe — count must never go negative
    // -------------------------------------------------------------------------

    @Test
    void should_maintainNonNegativeCount_when_subscribeAndUnsubscribeConcurrently()
            throws Exception {
        List<EventListener<TestEvent>> preRegistered = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            EventListener<TestEvent> l = e -> {
            };
            bus.subscribe(TestEvent.class, l);
            preRegistered.add(l);
        }

        AtomicReference<Throwable> caught = new AtomicReference<>();
        int half = THREADS / 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < half; i++) {
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < 30; j++) {
                        bus.subscribe(TestEvent.class, e -> {
                        });
                    }
                } catch (Exception ex) {
                    caught.set(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < half; i++) {
            final int idx = i % preRegistered.size();
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < 10; j++) {
                        bus.unsubscribe(TestEvent.class, preRegistered.get(idx));
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

        assertNull(caught.get());
        assertTrue(bus.listenerCount(TestEvent.class) >= 0,
                "Listener count must never be negative");
    }

    // -------------------------------------------------------------------------
    // 6. High-volume publishAsync — all CompletableFutures must complete
    // -------------------------------------------------------------------------

    @Test
    void should_completeAllFutures_when_highVolumePublishAsync() throws Exception {
        int total = THREADS * 10;
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(total);

        bus.subscribe(TestEvent.class, e -> {
            count.incrementAndGet();
            latch.countDown();
        });

        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch submitted = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        int perThread = total / THREADS;

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    for (int j = 0; j < perThread; j++) {
                        futures.add(bus.publishAsync(new TestEvent(j)));
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    submitted.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(submitted.await(10, TimeUnit.SECONDS));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.SECONDS);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(total, count.get());
    }

    // -------------------------------------------------------------------------
    // 7. Concurrent publish with async listeners — all invocations complete
    // -------------------------------------------------------------------------

    @Test
    void should_invokeAllAsyncListeners_when_publishConcurrently() throws Exception {
        int listenerCount = 10;
        CountDownLatch latch = new CountDownLatch(listenerCount * THREADS);

        for (int i = 0; i < listenerCount; i++) {
            bus.subscribeAsync(TestEvent.class, e -> latch.countDown());
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await(10, TimeUnit.SECONDS);
                    bus.publish(new TestEvent(id));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(latch.await(10, TimeUnit.SECONDS),
                "All async listeners must complete for all concurrent publishes");
        pool.shutdown();
    }

    // -------------------------------------------------------------------------
    // 8. Two event types published concurrently — no cross-type contamination
    // -------------------------------------------------------------------------

    @Test
    void should_notCrossContaminate_when_publishingMultipleEventTypesConcurrently()
            throws Exception {
        int perType = 100;
        AtomicInteger testCount = new AtomicInteger();
        AtomicInteger otherCount = new AtomicInteger();

        bus.subscribe(TestEvent.class, e -> testCount.incrementAndGet());
        bus.subscribe(OtherEvent.class, e -> otherCount.incrementAndGet());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        pool.submit(() -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < perType; i++) {
                    bus.publish(new TestEvent(i));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        pool.submit(() -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                for (int i = 0; i < perType; i++) {
                    bus.publish(new OtherEvent(i));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(perType, testCount.get(), "TestEvent listeners must not receive OtherEvent");
        assertEquals(perType, otherCount.get(), "OtherEvent listeners must not receive TestEvent");
    }

    // -------------------------------------------------------------------------
    // 9. AsyncListener graceful behavior after bus.close()
    // -------------------------------------------------------------------------

    @Test
    void should_notPropagateException_when_asyncListenerInvokedAfterClose() throws Exception {
        AtomicReference<Throwable> dispatcherException = new AtomicReference<>();

        // Subscribe a regular listener that captures any uncaught exception from dispatch
        bus.subscribe(TestEvent.class, e -> {
            // normal sync listener — must always run fine
        });
        bus.subscribeAsync(TestEvent.class, e -> {
            // async listener that will try to execute on possibly-shut-down executor
        });

        // Close the bus, then publish — AsyncListener.execute() on a shut-down executor
        // must not throw from the Dispatcher's perspective
        bus.close();

        // Should not throw — RejectedExecutionException must be caught in AsyncListener
        try {
            bus.publish(new TestEvent(1));
        } catch (Exception e) {
            dispatcherException.set(e);
        }

        assertNull(dispatcherException.get(),
                "publish() must not throw after bus is closed, even with async listeners");
    }
}
