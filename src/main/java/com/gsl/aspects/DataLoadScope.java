package com.gsl.aspects;

/**
 * Thread-local marker for the "test-data loading" phase.
 *
 * <p>While a thread is inside this scope, the framework's policy is that <em>any</em> exception
 * (typically {@link IllegalStateException} raised deep in the DB resolution chain) signals a
 * <b>data-availability shortfall</b> and will be converted to a {@link org.testng.SkipException}
 * at the data-source boundary — it is a skip (amber), not a failure (red).
 *
 * <p>{@link LoggingAspect} consults {@link #isActive()} in its exception advice: when the scope is
 * active it suppresses the per-frame {@code ExtentLogger.fail(...)} (+ screenshot) logging that
 * would otherwise paint the Extent node red <em>before</em> the exception is converted to a skip.
 * Without this, an {@code IllegalStateException} thrown by an inner helper logs one red FAIL entry
 * per advised frame it unwinds through, and ExtentReports renders the node at its highest-severity
 * log (FAIL &gt; WARNING/SKIP) — so a data-unavailability skip shows up as a misleading red fail.
 *
 * <p>Lives in {@code com.gsl.aspects}, which {@link LoggingAspect}'s pointcut already excludes via
 * {@code !within(com.gsl.aspects..*)}, so this class is never itself advised.
 *
 * <p>Reentrant: nested {@link #enter()} calls are balanced by matching {@link #exit()} calls so
 * the scope only deactivates once the outermost data-load frame completes.
 */
public final class DataLoadScope {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private DataLoadScope() {
    }

    /** Marks entry into a data-load phase on the current thread. Always pair with {@link #exit()} in a finally. */
    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    /** Marks exit from a data-load phase. Removes the thread-local once fully unwound to avoid leaks. */
    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    /** @return true when the current thread is inside a data-load phase. */
    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
