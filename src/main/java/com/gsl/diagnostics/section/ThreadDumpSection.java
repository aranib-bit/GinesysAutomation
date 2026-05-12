package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticSection;

import java.util.Map;

/**
 * Captures a JVM thread dump at the moment of test failure.
 * Intended for TimeoutException failures where a deadlock or hung thread may be responsible.
 *
 * Shows thread name, state, and top 10 stack frames per thread.
 * Only BLOCKED and WAITING threads are shown by default to keep the output concise.
 *
 * Config key: diagnostics.thread.dump.enabled = false
 */
public final class ThreadDumpSection implements DiagnosticSection {

    private static final int MAX_FRAMES_PER_THREAD = 10;

    private final String html;

    public ThreadDumpSection() {
        this.html = ConfigFactory.getConfig().diagnosticsThreadDumpEnabled()
                ? buildDump()
                : null;
    }

    @Override
    public String sectionTitle() {
        return "Thread Dump";
    }

    @Override
    public String renderHtml() {
        return html;
    }

    @Override
    public boolean hasData() {
        return html != null && !html.isBlank();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String buildDump() {
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:10px;'>");

        all.entrySet().stream()
                .filter(e -> isInteresting(e.getKey()))
                .sorted((a, b) -> a.getKey().getName().compareTo(b.getKey().getName()))
                .forEach(entry -> {
                    Thread t = entry.getKey();
                    StackTraceElement[] frames = entry.getValue();

                    String stateColour = switch (t.getState()) {
                        case BLOCKED  -> "#c0392b";
                        case WAITING, TIMED_WAITING -> "#e67e22";
                        default       -> "#2c3e50";
                    };

                    sb.append("<div style='margin-bottom:6px;'>")
                      .append("<b style='color:").append(stateColour).append(";'>")
                      .append(esc(t.getName()))
                      .append("</b>")
                      .append(" <span style='color:#888;'>[").append(t.getState()).append("]</span>")
                      .append("<pre style='margin:2px 0;padding:4px;background:#f5f5f5;")
                      .append("border-left:2px solid #bbb;font-size:10px;white-space:pre-wrap;'>");

                    int shown = Math.min(frames.length, MAX_FRAMES_PER_THREAD);
                    for (int i = 0; i < shown; i++) {
                        sb.append("  at ").append(esc(frames[i].toString())).append("\n");
                    }
                    if (frames.length > MAX_FRAMES_PER_THREAD) {
                        sb.append("  ... ").append(frames.length - MAX_FRAMES_PER_THREAD).append(" more frames\n");
                    }
                    sb.append("</pre></div>");
                });

        sb.append("</div>");
        return sb.toString();
    }

    private static boolean isInteresting(Thread t) {
        Thread.State s = t.getState();
        return s == Thread.State.BLOCKED
                || s == Thread.State.WAITING
                || s == Thread.State.TIMED_WAITING
                || t.getName().startsWith("pool-")
                || t.getName().startsWith("TestNG");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
