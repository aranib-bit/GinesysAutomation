package com.gsl.reports;

import com.gsl.config.ConfigFactory;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Thread-local ring buffer that records test step names as they execute.
 * Two separate knobs:
 *   action.trail.buffer.size   — how many steps to retain in memory (default 200)
 *   action.trail.display.limit — how many to show in ExtentReport (default 50)
 *
 * getAll()        → full untruncated trail (used by JsErrorJsonExporter)
 * getForDisplay() → last N entries (used by ActionTrailSection in ExtentReport)
 */
public final class ActionTrailBuffer {

    private static final int BUFFER_SIZE =
            ConfigFactory.getDiagnosticsConfig().actionTrailBufferSize();

    private static final ThreadLocal<ArrayDeque<String>> TRAIL =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(Math.max(BUFFER_SIZE, 1)));

    private ActionTrailBuffer() {}

    /** Appends {@code action} to this thread's trail, evicting the oldest entry once {@code BUFFER_SIZE} is reached. */
    public static void record(String action) {
        if (action == null || action.isBlank()) return;
        ArrayDeque<String> q = TRAIL.get();
        if (q.size() >= BUFFER_SIZE) q.pollFirst();
        q.addLast(action);
    }

    /** Full captured trail — never truncated. */
    public static List<String> getAll() {
        return List.copyOf(TRAIL.get());
    }

    /** Last N entries for ExtentReport display, respecting action.trail.display.limit. */
    public static List<String> getForDisplay() {
        int limit = ConfigFactory.getDiagnosticsConfig().actionTrailDisplayLimit();
        ArrayDeque<String> q = TRAIL.get();
        return q.stream().skip(Math.max(0, q.size() - limit)).toList();
    }

    /** Total steps captured so far on this thread — used for the footer "N of total" line. */
    public static int totalCaptured() {
        return TRAIL.get().size();
    }

    /** Clears this thread's trail — called at the start of each test. */
    public static void clear() {
        TRAIL.remove();
    }
}
