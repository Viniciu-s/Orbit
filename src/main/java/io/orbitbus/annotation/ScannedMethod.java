package io.orbitbus.annotation;

import io.orbitbus.core.Event;

import java.lang.reflect.Method;

/**
 * Holds the result of scanning a single {@code @Subscribe} method: the resolved
 * event type, its declared priority, and the reflective {@link Method} handle.
 *
 * <p>Package-private — internal use by {@link AnnotationScanner} and
 * {@link com.orbit.lib.core.OrbitEventBus} only.
 */
public record ScannedMethod(Class<? extends Event> eventType, Priority priority, Method method) {
}
