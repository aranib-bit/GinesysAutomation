package com.gsl.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local accumulator that records each retry attempt's details.
 *
 * Populated by {@link com.gsl.listeners.RetryAnalyzer} on every retry decision.
 * Consumed by {@link com.gsl.diagnostics.section.FlakySummarySection} to render
 * the flaky score and retry history table.
 *
 * No eviction — the maximum number of entries equals {@code retry.max.count}
 * (typically 2–3), so the list is always small.
 */
public final class RetryHistoryBuffer {

    public record RetryRecord(
            int attemptNumber,
            String exceptionType,
            String exceptionMessage,
            long timestampMs) {}

    private static final int MAX_MSG_LEN = 200;

    private static final ThreadLocal<List<RetryRecord>> HISTORY =
            ThreadLocal.withInitial(ArrayList::new);

    private RetryHistoryBuffer() {}

    /**
     * Records one retry attempt.
     *
     * @param attemptNumber 1-based retry counter (1 = first retry after the original failure)
     * @param cause         the Throwable that caused the failure; may be null
     */
    public static void record(int attemptNumber, Throwable cause) {
        String type = cause != null ? cause.getClass().getSimpleName() : "Unknown";
        String msg  = "";
        if (cause != null && cause.getMessage() != null) {
            msg = cause.getMessage();
            // First line only, truncated
            int nl = msg.indexOf('\n');
            if (nl >= 0) msg = msg.substring(0, nl);
            if (msg.length() > MAX_MSG_LEN) msg = msg.substring(0, MAX_MSG_LEN) + "…";
        }
        HISTORY.get().add(new RetryRecord(attemptNumber, type, msg, System.currentTimeMillis()));
    }

    /** Returns an unmodifiable view of all recorded retry attempts (oldest first). */
    public static List<RetryRecord> getAll() {
        return Collections.unmodifiableList(HISTORY.get());
    }

    /** Total number of retry attempts recorded on this thread. */
    public static int totalAttempts() {
        return HISTORY.get().size();
    }

    /** Clears all entries — called at the start of each test. */
    public static void clear() {
        HISTORY.remove();
    }
}
