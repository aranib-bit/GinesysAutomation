package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.RetryHistoryBuffer.RetryRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders retry analytics and a computed flaky score for tests that were retried.
 *
 * <p>Silently absent ({@link #hasData()} returns false) when:
 * <ul>
 *   <li>{@code retry.max.count=0} (default) — RetryHistoryBuffer is never populated.</li>
 *   <li>The test passed on the first attempt — no retry records exist.</li>
 * </ul>
 *
 * <p>Flaky score = {@code Math.round(retryAttempts / retryMaxCount * 100)}, clamped to [0, 100].
 * Score badges: 0–49 STABLE (green), 50–74 FLAKY (orange), 75–100 HIGHLY FLAKY (red).
 *
 * <p>Locator stability callouts surface well-known exception types that indicate
 * timing/DOM-stability issues, giving engineers an immediate root-cause hint.
 *
 * Registered in {@link com.gsl.diagnostics.FailureBundleOrchestrator} after the Wait Analysis.
 */
public final class FlakySummarySection implements DiagnosticSection {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final List<RetryRecord> records;
    private final int maxRetries;
    private final int warnThreshold;

    public FlakySummarySection(List<RetryRecord> records, int maxRetries, int warnThreshold) {
        this.records       = records;
        this.maxRetries    = maxRetries;
        this.warnThreshold = warnThreshold;
    }

    @Override
    public String sectionTitle() {
        return "Flaky Analysis — " + records.size() + " retry attempt(s)";
    }

    @Override
    public boolean hasData() {
        return !records.isEmpty();
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(buildScoreBlock());
        sb.append(buildRetryTable());
        sb.append(buildLocatorCallouts());
        return sb.toString();
    }

    // ── Flaky score ───────────────────────────────────────────────────────────

    private String buildScoreBlock() {
        int score = maxRetries > 0
                ? Math.min(100, (int) Math.round(records.size() * 100.0 / maxRetries))
                : 0;

        String badgeColor, badgeLabel;
        if (score >= 75)      { badgeColor = "#b71c1c"; badgeLabel = "HIGHLY FLAKY"; }
        else if (score >= warnThreshold) { badgeColor = "#e65100"; badgeLabel = "FLAKY"; }
        else                  { badgeColor = "#2e7d32"; badgeLabel = "STABLE"; }

        return "<div style='margin-bottom:10px;'>"
                // Badge
                + "<span style='background:" + badgeColor + ";color:white;padding:3px 10px;"
                + "border-radius:3px;font-size:11px;font-weight:bold;margin-right:10px;'>"
                + badgeLabel + "</span>"
                // Score label
                + "<span style='font-size:12px;font-family:monospace;'>Flaky Score: "
                + score + "%</span>"
                + "<div style='margin-top:6px;background:#e0e0e0;border-radius:3px;height:10px;width:100%;'>"
                + "<div style='background:" + badgeColor + ";height:10px;border-radius:3px;width:" + score + "%;'></div>"
                + "</div>"
                + "<div style='font-size:10px;color:#888;margin-top:3px;font-style:italic;'>"
                + records.size() + " retry attempt(s) out of max " + maxRetries
                + "</div></div>";
    }

    // ── Retry history table ───────────────────────────────────────────────────

    private String buildRetryTable() {
        StringBuilder rows = new StringBuilder();
        for (RetryRecord r : records) {
            String ts = Instant.ofEpochMilli(r.timestampMs())
                    .atZone(ZoneId.systemDefault())
                    .format(TS_FMT);
            rows.append("<tr>")
                .append(td("<b>Retry " + r.attemptNumber() + "</b>"))
                .append(td("<span style='color:#c62828;'>" + esc(r.exceptionType()) + "</span>"))
                .append(td(esc(r.exceptionMessage())))
                .append(td("<span style='color:#888;'>" + ts + "</span>"))
                .append("</tr>");
        }
        return tableWrap("Retry History",
                new String[]{"Attempt", "Exception Type", "Message", "Time"},
                rows.toString());
    }

    // ── Locator stability callouts ────────────────────────────────────────────

    private String buildLocatorCallouts() {
        Set<String> seen = new LinkedHashSet<>();
        for (RetryRecord r : records) seen.add(r.exceptionType());

        StringBuilder callouts = new StringBuilder();
        for (String type : seen) {
            String hint = locatorHint(type);
            if (hint == null) continue;
            callouts.append("<div style='margin-top:6px;padding:6px 10px;border-left:3px solid #e65100;"
                    + "background:#fff8e1;font-size:11px;'>")
                    .append("<b style='color:#e65100;'>&#9888; ").append(esc(type)).append("</b> — ")
                    .append(hint)
                    .append("</div>");
        }
        return callouts.toString();
    }

    private static String locatorHint(String exType) {
        return switch (exType) {
            case "StaleElementReferenceException" ->
                    "Stale DOM reference — element was detached between retries. Consider re-locating after each navigation.";
            case "NoSuchElementException" ->
                    "Element not found on retry — locator may be timing-sensitive. Add an explicit wait before interaction.";
            case "ElementClickInterceptedException" ->
                    "Click was intercepted on retry — overlay or mask may not have fully cleared. Check WaitUtils.waitForExtMask() coverage.";
            case "ElementNotInteractableException" ->
                    "Element not interactable on retry — element may be hidden or disabled after re-render.";
            default -> null;
        };
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
        return "<td style='border:1px solid #eee;padding:3px 6px;vertical-align:top;'>"
                + (content != null ? content : "") + "</td>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
