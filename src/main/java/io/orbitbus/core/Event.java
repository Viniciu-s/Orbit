package io.orbitbus.core;

/**
 * Marker interface for all events published on the Orbit Event Bus.
 *
 * <p>Any class (including Java records) that implements this interface
 * becomes a publishable event. The interface itself carries no methods —
 * each concrete event defines its own data contract.
 *
 * <p>Example:
 * <pre>{@code
 * public record UserCreatedEvent(String name) implements Event {}
 * }</pre>
 */
public interface Event {
}
