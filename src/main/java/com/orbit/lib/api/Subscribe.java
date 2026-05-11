package com.orbit.lib.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public method as an event listener to be auto-registered via
 * {@link EventBus#register(Object)}.
 *
 * <p>Rules for the annotated method:
 * <ul>
 *   <li>Must be {@code public}</li>
 *   <li>Must declare exactly one parameter</li>
 *   <li>The parameter type must implement {@link Event}</li>
 * </ul>
 *
 * <p>Violation of these rules causes {@link IllegalArgumentException} at
 * registration time (fail-fast).
 *
 * <p>Example:
 * <pre>{@code
 * public class UserHandler {
 *
 *     @Subscribe
 *     public void onUserCreated(UserCreatedEvent event) { ... }
 *
 *     @Subscribe(priority = Priority.HIGH)
 *     public void onUrgentUser(UserCreatedEvent event) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {

    /**
     * Execution priority of this listener relative to others registered for the
     * same event type.
     *
     * @return the priority; defaults to {@link Priority#NORMAL}
     */
    Priority priority() default Priority.NORMAL;
}
