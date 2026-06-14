package com.gsl.reports;

import com.gsl.config.ConfigFactory;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Thread-local ring buffer that records (stepName, durationMs, passed) tuples
 * from {@link StepExecutor} for each step that runs during a test.
 *
 * Follows the exact same pattern as {@link ActionTrailBuffer} — one ThreadLocal
 * ArrayDeque per test thread, capped at {@code action.trail.buffer.size} entries
 * (reuses the same config knob since timeline and trail represent the same depth).
 *
 * {@link com.gsl.diagnostics.section.StepTimelineSection} reads from this buffer
 * to render the horizontal bar chart.
 */
public final class StepDurationBuffer {

    public record StepRecord(String stepName, long durationMs, boolean passed) {}

    private static final int BUFFER_SIZE =
            ConfigFactory.getDiagnosticsConfig().actionTrailBufferSize();

    private static final ThreadLocal<ArrayDeque<StepRecord>> DURATIONS =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(Math.max(BUFFER_SIZE, 1)));

    private StepDurationBuffer() {}

    /** Records a step duration. Evicts the oldest entry when the buffer is full. */
    public static void record(String stepName, long durationMs, boolean passed) {
        if (stepName == null) return;
        ArrayDeque<StepRecord> q = DURATIONS.get();
        if (q.size() >= BUFFER_SIZE) q.pollFirst();
        q.addLast(new StepRecord(stepName, durationMs, passed));
    }

    /** Returns an immutable copy of all recorded step entries (oldest first). */
    public static List<StepRecord> getAll() {
        return List.copyOf(DURATIONS.get());
    }

    /** Clears all entries — called at the start of each test. */
    public static void clear() {
        DURATIONS.remove();
    }
}
