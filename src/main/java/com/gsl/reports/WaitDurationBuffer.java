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

    /**
     * @param attempts how many retry attempts the wait consumed — 1 for a plain single wait.
     *                 Retry loops (e.g. the Select-Doc loader loops) pass their real attempt count
     *                 so a wait worth 3 × the configured ceiling is visibly distinguishable in the
     *                 report from a single one that happened to be slow.
     */
    public record WaitRecord(String waitLabel, long durationMs, String waitMethod, int attempts) {}

    private static final int MAX = 100;

    private static final ThreadLocal<ArrayDeque<WaitRecord>> WAITS =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(MAX));

    private WaitDurationBuffer() {}

    /** Records a single wait call duration. Evicts the oldest entry when the buffer is full. */
    public static void record(String waitLabel, long durationMs, String waitMethod) {
        record(waitLabel, durationMs, waitMethod, 1);
    }

    /** Records a wait that consumed {@code attempts} retry attempts. */
    public static void record(String waitLabel, long durationMs, String waitMethod, int attempts) {
        ArrayDeque<WaitRecord> q = WAITS.get();
        if (q.size() >= MAX) q.pollFirst();
        q.addLast(new WaitRecord(
                waitLabel  != null ? waitLabel  : "",
                durationMs,
                waitMethod != null ? waitMethod : "",
                Math.max(1, attempts)));
    }

    /**
     * Starts a new test's wait window while PRESERVING the waits recorded just before it, retagged
     * with a {@code [pre-test]} prefix.
     *
     * <p>Replaces a plain {@link #clear()} at test start. TestNG fires {@code onTestStart} AFTER
     * {@code @BeforeMethod}, so clearing there silently destroyed every wait from login, module
     * navigation and data setup — often the slowest waits in the whole run, and exactly the ones
     * needed when a test fails on a mask during setup. They are kept and labelled rather than
     * dropped, so they're still attributable but never confused with the test body's own waits.
     *
     * <p>Note the preserved window spans from the previous test's teardown through this test's
     * setup (nothing clears at test end), which is why the label says "pre-test" rather than
     * naming a specific phase.
     */
    public static void startNewTest() {
        List<WaitRecord> preTest = List.copyOf(WAITS.get());
        WAITS.remove();
        for (WaitRecord r : preTest) {
            record(r.waitLabel().startsWith("[pre-test]") ? r.waitLabel() : "[pre-test] " + r.waitLabel(),
                    r.durationMs(), r.waitMethod(), r.attempts());
        }
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
