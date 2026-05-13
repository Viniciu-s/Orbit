package io.orbitbus.exception;

/**
 * Callback invoked whenever a listener throws during event dispatch.
 *
 * <p>Register a custom handler via {@code EventBus.setErrorHandler(ErrorHandler)}.
 * The default behaviour (when no custom handler is set) is to log the failure
 * via SLF4J at ERROR level.
 *
 * <p>Implementations must not throw — any exception thrown by the handler
 * is silently swallowed and logged internally.
 */
@FunctionalInterface
public interface ErrorHandler {

    /**
     * Called when a listener fails during dispatch.
     *
     * @param error the exception thrown by the listener; never {@code null}
     */
    void onError(Throwable error);
}
