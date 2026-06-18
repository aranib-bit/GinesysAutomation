package com.gsl.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local buffer that captures {@code [DB Query]} lines during the
 * data-loading phase ({@link com.gsl.utils.dataprovider.source.finance.DbApVoucherDataSource#loadFreshForScenario}).
 *
 * <p>While the buffer is active, {@link JdbiSqlLogger} routes resolved query lines
 * here instead of directly to {@link com.gsl.reports.ReportLogger}. At the end of
 * {@code loadFreshForScenario} the collected lines are flushed into
 * {@code buildAndSetGatheringLog()}, which renders them inside the
 * "🗄 Database Data Gathering" child node in the Extent Report.
 *
 * <p>Queries fired <em>outside</em> the data-loading phase (e.g., in validators) are
 * unaffected — they continue to appear as flat {@code ReportLogger.info()} entries.
 *
 * <p>Mirrors the reentrant pattern of {@link com.gsl.aspects.DataLoadScope}:
 * always call {@link #flush()} in a {@code finally} block to prevent leaks.
 */
public final class DbQueryBuffer {

    private static final ThreadLocal<List<String>> BUFFER = new ThreadLocal<>();

    private DbQueryBuffer() {}

    /** Activates buffering on the current thread. Always pair with {@link #flush()} in a finally. */
    public static void start() {
        BUFFER.set(new ArrayList<>());
    }

    /** @return {@code true} when the current thread has an active buffer. */
    public static boolean isActive() {
        return BUFFER.get() != null;
    }

    /** Adds a resolved query line to the buffer. No-op if buffer is not active. */
    public static void add(String line) {
        List<String> buf = BUFFER.get();
        if (buf != null) buf.add(line);
    }

    /**
     * Returns all buffered lines and removes the buffer from this thread.
     * Returns an empty list if the buffer was never started or already flushed.
     */
    public static List<String> flush() {
        List<String> result = BUFFER.get();
        BUFFER.remove();
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }
}
