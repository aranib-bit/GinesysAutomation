package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.WaitDurationBuffer.WaitRecord;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a two-part view of time spent inside {@link com.gsl.utils.Selenium.WaitUtils} calls.
 *
 * <p><b>Part 1 — Summary table:</b> grouped by wait method with call count, total, average,
 * and max duration.  Rows where max &ge; slowThresholdMs are highlighted.
 *
 * <p><b>Part 2 — Detail table</b> (inside a collapsible {@code <details>} block): every
 * individual wait record with its label, method name, and duration.
 *
 * Registered in {@link com.gsl.diagnostics.FailureBundleOrchestrator} after the Step Timeline.
 */
public final class WaitAnalysisSection implements DiagnosticSection {

    private final List<WaitRecord> records;
    private final long slowThresholdMs;
    private final long totalWaitMs;

    public WaitAnalysisSection(List<WaitRecord> records, long slowThresholdMs) {
        this.records        = records;
        this.slowThresholdMs = slowThresholdMs;
        this.totalWaitMs    = records.stream().mapToLong(WaitRecord::durationMs).sum();
    }

    @Override
    public String sectionTitle() {
        return "Wait Analysis — " + totalWaitMs + "ms total wait time";
    }

    @Override
    public boolean hasData() {
        return !records.isEmpty();
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSummaryTable());
        sb.append(buildDetailTable());
        return sb.toString();
    }

    // ── Summary table (grouped by method) ────────────────────────────────────

    private String buildSummaryTable() {
        // Group by waitMethod
        Map<String, List<WaitRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(WaitRecord::waitMethod,
                        LinkedHashMap::new, Collectors.toList()));

        StringBuilder rows = new StringBuilder();
        for (Map.Entry<String, List<WaitRecord>> e : grouped.entrySet()) {
            List<WaitRecord> group = e.getValue();
            long total = group.stream().mapToLong(WaitRecord::durationMs).sum();
            long avg   = total / group.size();
            long max   = group.stream().mapToLong(WaitRecord::durationMs).max().orElse(0);
            long slow  = group.stream().filter(r -> r.durationMs() >= slowThresholdMs).count();

            String rowBg = max >= slowThresholdMs ? "background:#fff3e0;" : "";
            rows.append("<tr style='").append(rowBg).append("'>")
                .append(td(esc(e.getKey())))
                .append(td(String.valueOf(group.size())))
                .append(td(total + "ms"))
                .append(td(avg   + "ms"))
                .append(td(max   + "ms"))
                .append(td(slow > 0
                        ? "<span style='color:#e65100;font-weight:bold;'>" + slow + "</span>"
                        : "0"))
                .append("</tr>");
        }
        return tableWrap("Summary by Wait Method",
                new String[]{"Wait Method", "Calls", "Total (ms)", "Avg (ms)", "Max (ms)", "Slow Calls"},
                rows.toString());
    }

    // ── Detail table (collapsible) ────────────────────────────────────────────

    private String buildDetailTable() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            WaitRecord r = records.get(i);
            boolean slow = r.durationMs() >= slowThresholdMs;
            String rowBg = slow ? "background:#ffebee;" : "";
            rows.append("<tr style='").append(rowBg).append("'>")
                .append(td(String.valueOf(i + 1)))
                .append(td(esc(r.waitLabel())))
                .append(td(esc(r.waitMethod())))
                .append(td(r.durationMs() + "ms"))
                // Attempts > 1 means a retry loop consumed this time — a 120s wait made of
                // 3 × 40s attempts is a very different diagnosis from one 120s wait, and that
                // distinction was previously invisible here.
                .append(td(r.attempts() > 1
                        ? "<b style='color:#e65100;'>" + r.attempts() + "</b>"
                        : String.valueOf(r.attempts())))
                .append(td(slow ? "<b style='color:#b71c1c;'>YES</b>" : ""))
                .append("</tr>");
        }
        String table = tableWrap("All Wait Records",
                new String[]{"#", "Label", "Method", "Duration", "Attempts", "Slow?"},
                rows.toString());
        return "<details style='margin-top:8px;'>"
                + "<summary style='cursor:pointer;font-size:11px;font-weight:600;color:#444;'>"
                + "All wait records (" + records.size() + ")</summary>"
                + table
                + "</details>";
    }

    // ── HTML helpers ──────────────────────────────────────────────────────────

    private static String tableWrap(String title, String[] headers, String bodyRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top:8px;'>")
          .append("<div style='font-size:11px;font-weight:600;margin-bottom:4px;color:#444;'>")
          .append(title).append("</div>")
          .append("<table style='font-size:11px;border-collapse:collapse;width:100%;'>")
          .append("<thead><tr style='background:#f0f0f0;'>");
        for (String h : headers) {
            sb.append("<th style='border:1px solid #ccc;padding:3px 6px;text-align:left;'>")
              .append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>").append(bodyRows).append("</tbody></table></div>");
        return sb.toString();
    }

    private static String td(String content) {
        return "<td style='border:1px solid #eee;padding:2px 6px;vertical-align:top;'>"
                + (content != null ? content : "") + "</td>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
