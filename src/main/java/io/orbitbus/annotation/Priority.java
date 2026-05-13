package io.orbitbus.annotation;

/**
 * Defines the execution priority of a listener registered on the EventBus.
 *
 * <p>When an event is published, listeners are invoked in priority order:
 * {@link #HIGH} listeners run first, {@link #NORMAL} in the middle, and
 * {@link #LOW} last. Listeners with the same priority are invoked in the
 * order they were registered (FIFO).
 *
 * <p>The ordinal value of each constant determines the sort key — smaller
 * ordinals execute earlier, so the declaration order (HIGH → NORMAL → LOW)
 * matches the execution order.
 */
public enum Priority {

    /** Highest precedence — executes before {@link #NORMAL} and {@link #LOW}. */
    HIGH,

    /**
     * Default precedence — used when no priority is specified.
     */
    NORMAL,

    /** Lowest precedence — executes after {@link #HIGH} and {@link #NORMAL}. */
    LOW
}
