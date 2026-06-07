package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.StepDurationBuffer.StepRecord;

import java.util.List;

/**
 * Renders a horizontal bar chart of step durations captured by {@link com.gsl.reports.StepDurationBuffer}.
 *
 * <p>Each step becomes one row: a coloured progress bar scaled relative to the longest step,
 * plus the raw millisecond value and a FAILED marker where applicable.
 *
 * <p>Bar colours:
 * <ul>
 *   <li>Green  — passed and duration &lt; warn threshold</li>
 *   <li>Orange — passed but duration &ge; warn threshold (slow step)</li>
 *   <li>Red    — failed step</li>
 * </ul>
 *
 * Registered in {@link com.gsl.diagnostics.FailureBundleOrchestrator} after the Action Trail.
 */
public final class StepTimelineSection implements DiagnosticSection {

    private final List<StepRecord> records;
    private final long warnMs;

    public StepTimelineSection(List<StepRecord> records, long warnMs) {
        this.records = records;
        this.warnMs  = warnMs;
    }

    @Override
    public String sectionTitle() {
        long totalMs = records.stream().mapToLong(StepRecord::durationMs).sum();
        return "Step Timeline — " + records.size() + " step(s) | " + totalMs + "ms total";
    }

    @Override
    public boolean hasData() {
        return !records.isEmpty();
    }

    @Override
    public String renderHtml() {
        long maxDuration = records.stream().mapToLong(StepRecord::durationMs).max().orElse(1);
        if (maxDuration == 0) maxDuration = 1;  // guard against all-zero durations

        StringBuilder sb = new StringBuilder();
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:11px;font-family:monospace;'>")
          .append("<thead><tr style='background:#f0f0f0;'>")
          .append("<th style='").append(TH).append("width:24px;'>#</th>")
          .append("<th style='").append(TH).append("width:35%;'>Step Name</th>")
          .append("<th style='").append(TH).append("'>Duration</th>")
          .append("</tr></thead><tbody>");

        for (int i = 0; i < records.size(); i++) {
            StepRecord r = records.get(i);
            int widthPct = (int) Math.max(1, Math.round(r.durationMs() * 100.0 / maxDuration));
            String barColor = barColor(r);

            sb.append("<tr>")
              .append("<td style='").append(TD).append("color:#888;'>").append(i + 1).append("</td>")
              .append("<td style='").append(TD).append("max-width:0;overflow:hidden;")
              .append("text-overflow:ellipsis;white-space:nowrap;' title='").append(esc(r.stepName())).append("'>")
              .append(esc(r.stepName())).append("</td>")
              .append("<td style='").append(TD).append("'>")
              // Bar
              .append("<div style='display:inline-block;width:").append(widthPct)
              .append("%;height:12px;background:").append(barColor)
              .append(";border-radius:2px;vertical-align:middle;margin-right:6px;'></div>")
              // Label
              .append("<span style='vertical-align:middle;'>").append(r.durationMs()).append("ms")
              .append(!r.passed() ? " <b style='color:#b71c1c;'>(FAILED)</b>" : "")
              .append("</span>")
              .append("</td></tr>");
        }

        sb.append("</tbody></table>");

        // Footer — total duration
        long totalMs = records.stream().mapToLong(StepRecord::durationMs).sum();
        sb.append("<div style='margin-top:4px;font-size:10px;color:#888;font-style:italic;'>")
          .append("Total: ").append(totalMs).append("ms across ").append(records.size()).append(" step(s)")
          .append("</div>");

        return sb.toString();
    }

    private String barColor(StepRecord r) {
        if (!r.passed())                  return "#b71c1c";  // red   — failed
        if (r.durationMs() >= warnMs)     return "#e65100";  // orange — slow
        return "#2e7d32";                                     // green  — normal
    }

    private static final String TH = "border:1px solid #ccc;padding:3px 6px;text-align:left;";
    private static final String TD = "border:1px solid #eee;padding:2px 6px;vertical-align:middle;";

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
