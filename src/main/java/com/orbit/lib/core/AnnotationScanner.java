package com.orbit.lib.core;

import com.orbit.lib.api.Event;
import com.orbit.lib.api.Subscribe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans an object for public methods annotated with {@link Subscribe} and
 * returns the validated metadata needed to register them on the bus.
 *
 * <p>Only public methods (as returned by {@link Class#getMethods()}) are
 * considered — this includes methods inherited from superclasses and
 * interfaces.
 *
 * <p>Package-private — internal use only.
 */
final class AnnotationScanner {

    private AnnotationScanner() {
    }

    /**
     * Scans {@code handler} for {@link Subscribe}-annotated methods.
     *
     * @param handler the object to scan; must not be {@code null}
     * @return an ordered list of scanned method descriptors; may be empty
     * @throws IllegalArgumentException if any annotated method has an invalid signature
     */
    static List<ScannedMethod> scan(Object handler) {
        List<ScannedMethod> result = new ArrayList<>();
        for (Method method : handler.getClass().getMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }
            validate(handler.getClass(), method);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType =
                    (Class<? extends Event>) method.getParameterTypes()[0];
            result.add(new ScannedMethod(eventType, annotation.priority(), method));
        }
        return result;
    }

    private static void validate(Class<?> handlerClass, Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1) {
            throw new IllegalArgumentException(
                    "@Subscribe method '" + method.getName() + "' in "
                            + handlerClass.getSimpleName()
                            + " must have exactly one parameter, but has " + params.length);
        }
        if (!Event.class.isAssignableFrom(params[0])) {
            throw new IllegalArgumentException(
                    "@Subscribe method '" + method.getName() + "' in "
                            + handlerClass.getSimpleName()
                            + " parameter must implement Event, but was "
                            + params[0].getName());
        }
    }
}
