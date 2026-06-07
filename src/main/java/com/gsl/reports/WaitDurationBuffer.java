package com.gsl.reports;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Thread-local ring buffer that records how long each {@code WaitUtils} call consumed.
 *
 * Capacity is capped at 100 entries (hardcoded — wait calls are far less frequent
 * than step calls and a configurable knob is unnecessary).
 *
 * {@link com.gsl.diagnostics.section.WaitAnalysisSection} reads from this buffer
 * to render the summary + detail wait tables.
 */
public final class WaitDurationBuffer {

    public record WaitRecord(String waitLabel, long durationMs, String waitMethod) {}

    private static final int MAX = 100;

    private static final ThreadLocal<ArrayDeque<WaitRecord>> WAITS =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(MAX));

    private WaitDurationBuffer() {}

    /** Records a single wait call duration. Evicts the oldest entry when the buffer is full. */
    public static void record(String waitLabel, long durationMs, String waitMethod) {
        ArrayDeque<WaitRecord> q = WAITS.get();
        if (q.size() >= MAX) q.pollFirst();
        q.addLast(new WaitRecord(
                waitLabel  != null ? waitLabel  : "",
                durationMs,
                waitMethod != null ? waitMethod : ""));
    }

    /** Returns an immutable copy of all recorded wait entries (oldest first). */
    public static List<WaitRecord> getAll() {
        return List.copyOf(WAITS.get());
    }

    /** Sum of all recorded durations — used by {@code WaitAnalysisSection.sectionTitle()}. */
    public static long totalWaitMs() {
        return WAITS.get().stream().mapToLong(WaitRecord::durationMs).sum();
    }

    /** Clears all entries — called at the start of each test. */
    public static void clear() {
        WAITS.remove();
    }
}
