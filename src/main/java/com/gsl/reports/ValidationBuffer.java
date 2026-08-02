package com.gsl.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local line buffer for DB validation levels.
 *
 * <p>When active, both {@link ReportLogger#info(String)} and
 * {@link com.gsl.database.JdbiSqlLogger} route their lines here instead of
 * writing individual Info entries to the Extent Report.  The caller flushes
 * the accumulated lines as a single blue {@code <pre>} block immediately
 * before logging the result table, producing the compact layout:
 *
 * <pre>
 *   [header Info row]
 *   [blue pre block — all DB queries + intermediate log lines]
 *   [styled result table]
 *   [green field-checks summary]
 *   [PASSED Info row]
 * </pre>
 */
public final class ValidationBuffer {

    private static final ThreadLocal<List<String>> LINES = new ThreadLocal<>();

    private ValidationBuffer() {}

    /** Activates the buffer on this thread. Any previous contents are discarded. */
    public static void start() {
        LINES.set(new ArrayList<>());
    }

    /** Returns {@code true} when the buffer is active on this thread. */
    public static boolean isActive() {
        return LINES.get() != null;
    }

    /** Appends {@code line} to the buffer. No-op when inactive. */
    public static void add(String line) {
        List<String> buf = LINES.get();
        if (buf != null) buf.add(line);
    }

    /**
     * Deactivates the buffer and renders all accumulated lines as one blue
     * {@code <pre>} Info entry on the current Extent test.  No-op (and safe to
     * call) when the buffer is empty or inactive.
     */
    public static void flushAsPreBlock() {
        List<String> lines = LINES.get();
        LINES.remove();
        if (lines == null || lines.isEmpty()) return;
        if (!ExtentManager.hasTest()) return;

        StringBuilder html = new StringBuilder(
            "<pre style='font-size:11px;font-family:monospace;margin:0;white-space:pre-wrap;" +
            "background:#f8fbff;padding:8px 10px;border-left:3px solid #6aacf0;" +
            "line-height:1.7;'>");
        for (String line : lines) {
            html.append(escHtml(line)).append("\n");
        }
        html.append("</pre>");
        ExtentLogger.infoHtml(html.toString());
    }

    /** Discards buffer contents without rendering — use on error paths. */
    public static void discard() {
        LINES.remove();
    }

    /** Returns an unmodifiable snapshot of current buffer contents (for testing/debugging). */
    public static List<String> peek() {
        List<String> buf = LINES.get();
        return buf != null ? Collections.unmodifiableList(buf) : Collections.emptyList();
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
