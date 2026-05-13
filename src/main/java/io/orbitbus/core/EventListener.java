package io.orbitbus.core;

/**
 * Functional interface for handling events published on the Orbit Event Bus.
 *
 * <p>Implementations react to a specific event type {@code T}. Because this
 * is a {@code @FunctionalInterface}, listeners can be expressed as lambdas:
 *
 * <pre>{@code
 * orbit.subscribe(UserCreatedEvent.class, event -> {
 *     System.out.println("New user: " + event.name());
 * });
 * }</pre>
 *
 * @param <T> the concrete {@link Event} type this listener handles
 */
@FunctionalInterface
public interface EventListener<T extends Event> {

    /**
     * Called when an event of type {@code T} is published on the bus.
     *
     * @param event the published event; never {@code null}
     */
    void onEvent(T event);
}
