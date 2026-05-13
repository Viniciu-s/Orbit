package io.orbitbus;

import io.orbitbus.core.EventBus;
import io.orbitbus.core.OrbitEventBus;

/**
 * Entry point for the Orbit Event Bus library.
 *
 * <p>Use {@link #create()} to obtain a new, independent {@link EventBus} instance:
 *
 * <pre>{@code
 * Orbit.create()
 *     .subscribe(UserCreatedEvent.class, event -> System.out.println(event.name()))
 *     .publish(new UserCreatedEvent("Vinicius"));
 * }</pre>
 *
 * <p>Each call to {@code create()} returns a fresh bus with no listeners registered.
 * Multiple buses can coexist in the same application without interfering with each other.
 */
public final class Orbit {

    private Orbit() {
        // utility class — not instantiable
    }

    /**
     * Creates a new, empty {@link EventBus} instance.
     *
     * @return a fresh thread-safe {@code EventBus}
     */
    public static EventBus create() {
        return new OrbitEventBus();
    }
}
