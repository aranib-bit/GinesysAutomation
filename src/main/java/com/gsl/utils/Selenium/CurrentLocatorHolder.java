package com.gsl.utils.Selenium;

/**
 * Thread-local holder for the most recently used element locator.
 *
 * <p>Page-object methods that interact with elements should call
 * {@link #set(String)} before each {@code findElement()} / {@code click()} /
 * {@code sendKeys()} call so that on failure the locator is available for
 * screenshot metadata without requiring exception-chain inspection.
 *
 * <p>The value is cleared by {@link #clear()} — call this in StepExecutor's
 * finally block or after a successful screenshot capture.
 */
public final class CurrentLocatorHolder {

    private static final ThreadLocal<String> LOCATOR = new ThreadLocal<>();

    private CurrentLocatorHolder() {}

    /** Records the locator that will be used in the next element interaction. */
    public static void set(String locator) {
        LOCATOR.set(locator);
    }

    /** Returns the last recorded locator, or {@code null} if none set. */
    public static String get() {
        return LOCATOR.get();
    }

    /** Clears the stored locator (call after screenshot capture or step teardown). */
    public static void clear() {
        LOCATOR.remove();
    }
}
