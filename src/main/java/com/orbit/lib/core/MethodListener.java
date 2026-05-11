package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.EventListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * An {@link EventListener} that routes invocations to a reflective {@link Method}
 * on a target object.
 *
 * <p>If the method throws, the cause is re-wrapped in a {@link RuntimeException}
 * so the {@link Dispatcher} can catch and log it like any other listener failure.
 *
 * <p>Package-private — internal use only.
 *
 * @param <T> the event type accepted by the wrapped method
 */
final class MethodListener<T extends Event> implements EventListener<T> {

    private final Object target;
    private final Method method;

    MethodListener(Object target, Method method) {
        this.target = target;
        this.method = method;
    }

    @Override
    public void onEvent(T event) {
        try {
            method.invoke(target, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(
                    "@Subscribe method '" + method.getName() + "' threw: "
                            + (cause != null ? cause.getMessage() : e.getMessage()),
                    cause != null ? cause : e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Cannot invoke @Subscribe method '" + method.getName() + "'", e);
        }
    }
}
