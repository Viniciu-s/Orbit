package io.orbitbus.core;

import io.orbitbus.pipeline.EventInterceptor;
import io.orbitbus.annotation.Priority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitEventBusChannelTest {

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

    // ---- Basic channel isolation ------------------------------------------

    @Test
    void should_isolateChannels_whenPublishingToSeparateChannels() {
        List<PaymentEvent> paymentsReceived = new ArrayList<>();
        List<PaymentEvent> ordersReceived = new ArrayList<>();

        bus.channel("payments").subscribe(PaymentEvent.class, paymentsReceived::add);
        bus.channel("orders").subscribe(PaymentEvent.class, ordersReceived::add);

        bus.channel("payments").publish(new PaymentEvent(100.0));

        assertEquals(1, paymentsReceived.size());
        assertTrue(ordersReceived.isEmpty());
    }

    @Test
    void should_notReceiveEventOnDefaultChannel_whenPublishedToNamedChannel() {
        List<PaymentEvent> defaultReceived = new ArrayList<>();
        List<PaymentEvent> namedReceived = new ArrayList<>();

        bus.subscribe(PaymentEvent.class, defaultReceived::add);
        bus.channel("payments").subscribe(PaymentEvent.class, namedReceived::add);

        bus.channel("payments").publish(new PaymentEvent(50.0));

        assertTrue(defaultReceived.isEmpty());
        assertEquals(1, namedReceived.size());
    }

    @Test
    void should_notReceiveEventOnNamedChannel_whenPublishedToDefaultChannel() {
        List<PaymentEvent> defaultReceived = new ArrayList<>();
        List<PaymentEvent> namedReceived = new ArrayList<>();

        bus.subscribe(PaymentEvent.class, defaultReceived::add);
        bus.channel("payments").subscribe(PaymentEvent.class, namedReceived::add);

        bus.publish(new PaymentEvent(75.0));

        assertEquals(1, defaultReceived.size());
        assertTrue(namedReceived.isEmpty());
    }

    // ---- Same channel name returns isolated instances ---------------------

    @Test
    void should_receiveEvent_whenPublishedToSameChannelName() {
        List<PaymentEvent> received = new ArrayList<>();

        EventBus channelA = bus.channel("payments");
        EventBus channelB = bus.channel("payments");

        channelA.subscribe(PaymentEvent.class, received::add);
        channelB.publish(new PaymentEvent(200.0));

        assertEquals(1, received.size());
    }

    @Test
    void should_returnDifferentInstance_whenChannelCalledMultipleTimes() {
        EventBus ch1 = bus.channel("payments");
        EventBus ch2 = bus.channel("payments");

        assertNotSame(ch1, ch2);
    }

    // ---- Priority works per channel ---------------------------------------

    @Test
    void should_respectPriority_withinChannel() {
        List<String> order = new ArrayList<>();
        EventBus payments = bus.channel("payments");

        payments.subscribe(PaymentEvent.class, Priority.LOW, e -> order.add("low"));
        payments.subscribe(PaymentEvent.class, Priority.HIGH, e -> order.add("high"));
        payments.subscribe(PaymentEvent.class, Priority.NORMAL, e -> order.add("normal"));

        payments.publish(new PaymentEvent(10.0));

        assertEquals(List.of("high", "normal", "low"), order);
    }

    // ---- Async features ---------------------------------------------------

    @Test
    void should_invokeAsyncListener_withinChannel() throws Exception {
        List<PaymentEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        EventBus payments = bus.channel("payments");

        payments.subscribeAsync(PaymentEvent.class, e -> {
            received.add(e);
            latch.countDown();
        });

        payments.publish(new PaymentEvent(99.0));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
    }

    @Test
    void should_publishAsync_withinChannel() throws Exception {
        List<PaymentEvent> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        EventBus payments = bus.channel("payments");

        payments.subscribe(PaymentEvent.class, e -> {
            received.add(e);
            latch.countDown();
        });

        payments.publishAsync(new PaymentEvent(88.0)).get(2, TimeUnit.SECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
    }

    // ---- once() works per channel -----------------------------------------

    @Test
    void should_fireOnce_withinChannel() {
        List<PaymentEvent> received = new ArrayList<>();
        EventBus payments = bus.channel("payments");

        payments.once(PaymentEvent.class, received::add);

        payments.publish(new PaymentEvent(1.0));
        payments.publish(new PaymentEvent(2.0));

        assertEquals(1, received.size());
    }

    // ---- Wildcard listeners per channel -----------------------------------

    @Test
    void should_invokeWildcardListener_onlyForEventsInSameChannel() {
        List<Event> paymentsAll = new ArrayList<>();
        List<Event> ordersAll = new ArrayList<>();

        bus.channel("payments").subscribeAll(paymentsAll::add);
        bus.channel("orders").subscribeAll(ordersAll::add);

        bus.channel("payments").publish(new PaymentEvent(10.0));
        bus.channel("orders").publish(new OrderEvent("O1"));

        assertEquals(1, paymentsAll.size());
        assertEquals(1, ordersAll.size());
        assertTrue(paymentsAll.get(0) instanceof PaymentEvent);
        assertTrue(ordersAll.get(0) instanceof OrderEvent);
    }

    // ---- Interceptors per channel -----------------------------------------

    @Test
    void should_callInterceptor_onlyForEventsInSameChannel() {
        List<String> log = new ArrayList<>();
        EventBus payments = bus.channel("payments");
        EventBus orders = bus.channel("orders");

        payments.addInterceptor(new EventInterceptor() {
            @Override
            public void beforePublish(Event event) {
                log.add("payments-before");
            }

            @Override
            public void afterPublish(Event event) {
                log.add("payments-after");
            }
        });

        orders.publish(new OrderEvent("O1"));
        assertTrue(log.isEmpty());

        payments.publish(new PaymentEvent(5.0));
        assertEquals(List.of("payments-before", "payments-after"), log);
    }

    // ---- unsubscribe per channel ------------------------------------------

    @Test
    void should_unsubscribeFromCorrectChannel() {
        List<PaymentEvent> received = new ArrayList<>();
        EventBus payments = bus.channel("payments");
        EventBus orders = bus.channel("orders");

        EventListener<PaymentEvent> listener = received::add;
        payments.subscribe(PaymentEvent.class, listener);
        orders.subscribe(PaymentEvent.class, listener);

        payments.unsubscribe(PaymentEvent.class, listener);

        payments.publish(new PaymentEvent(1.0));
        orders.publish(new PaymentEvent(2.0));

        assertEquals(1, received.size());
        assertEquals(2.0, received.get(0).amount());
    }

    // ---- listenerCount per channel ----------------------------------------

    @Test
    void should_countListeners_perChannel() {
        EventBus payments = bus.channel("payments");
        EventBus orders = bus.channel("orders");

        payments.subscribe(PaymentEvent.class, e -> {
        });
        payments.subscribe(PaymentEvent.class, e -> {
        });
        orders.subscribe(PaymentEvent.class, e -> {
        });

        assertEquals(2, payments.listenerCount(PaymentEvent.class));
        assertEquals(1, orders.listenerCount(PaymentEvent.class));
        assertEquals(0, bus.listenerCount(PaymentEvent.class));
    }

    // ---- ErrorHandler per channel -----------------------------------------

    @Test
    void should_useChannelErrorHandler_whenSet() {
        List<Throwable> captured = new ArrayList<>();
        EventBus payments = bus.channel("payments");

        payments.setErrorHandler(captured::add);
        payments.subscribe(PaymentEvent.class, e -> {
            throw new RuntimeException("boom");
        });

        payments.publish(new PaymentEvent(1.0));

        assertEquals(1, captured.size());
    }

    // ---- Fluent API -------------------------------------------------------

    @Test
    void should_returnSameBusInstance_whenChannelCalled() {
        EventBus result = bus.channel("payments");
        assertSame(result, result.channel("nested"));
    }

    @Test
    void should_supportFluentChaining_onChannel() {
        EventBus payments = bus.channel("payments");
        EventBus result = payments
                .subscribe(PaymentEvent.class, e -> {
                })
                .subscribeAsync(OrderEvent.class, e -> {
                });

        assertSame(payments, result);
    }

    // ---- NPE guards -------------------------------------------------------

    @Test
    void should_throwNullPointerException_when_channelReceivesNull() {
        assertThrows(NullPointerException.class, () -> bus.channel(null));
    }

    // ---- Close propagation ------------------------------------------------

    @Test
    void should_closeAllChannels_when_rootBusClosed() throws Exception {
        EventBus payments = bus.channel("payments");

        bus.close();

        // After close, async operations should not execute
        List<PaymentEvent> received = new ArrayList<>();
        payments.subscribeAsync(PaymentEvent.class, received::add);
        payments.publish(new PaymentEvent(1.0));
        Thread.sleep(100);

        assertTrue(received.isEmpty());
    }

    // ---- Annotation-based registration per channel ------------------------

    @Test
    void should_registerAnnotatedHandler_perChannel() {
        List<PaymentEvent> paymentsReceived = new ArrayList<>();
        List<PaymentEvent> ordersReceived = new ArrayList<>();

        class PaymentHandler {
            @io.orbitbus.annotation.Subscribe
            public void onPayment(PaymentEvent event) {
                paymentsReceived.add(event);
            }
        }

        class OrderHandler {
            @io.orbitbus.annotation.Subscribe
            public void onPayment(PaymentEvent event) {
                ordersReceived.add(event);
            }
        }

        bus.channel("payments").register(new PaymentHandler());
        bus.channel("orders").register(new OrderHandler());

        bus.channel("payments").publish(new PaymentEvent(10.0));

        assertEquals(1, paymentsReceived.size());
        assertTrue(ordersReceived.isEmpty());
    }
}
