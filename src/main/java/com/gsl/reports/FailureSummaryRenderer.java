package com.gsl.reports;

import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.RootCause;
import com.gsl.enums.ReportSeverity;
import com.gsl.reports.profile.RuntimeExecutionProfile;
import com.gsl.utils.TestContextManager;
import org.testng.ITestResult;

import java.util.List;

/**
 * Renders a compact failure-summary box as the first visible entry after the fail
 * status marker, so readers see the critical context immediately without scrolling
 * through the full diagnostic bundle.
 *
 * Rendered once per failed test from {@code TestListener.onTestFailure()} before
 * {@code FailureBundleOrchestrator.onFailure()} attaches the full evidence bundle.
 */
public final class FailureSummaryRenderer {

    private FailureSummaryRenderer() {}

    /** Backward-compatible entry point — renders the summary box with no root-cause banner. */
    public static void render(ITestResult result) {
        render(result, null);
    }

    /**
     * Renders the failure summary box, optionally enriched with an R-04 root-cause verdict.
     * When {@code rootCause} is non-null a color-coded "FAILURE ROOT CAUSE" banner is prepended
     * inside the box and Root Cause / Confidence rows are appended to the data table.
     */
    public static void render(ITestResult result, RootCause rootCause) {
        if (ExtentManager.getExtentTest() == null) return;

        Throwable t = result.getThrowable();
        String exType   = t != null ? t.getClass().getSimpleName()  : "Unknown";
        String exMsg    = t != null ? firstLine(t.getMessage(), 350) : "No message";

        List<String> trail = ActionTrailBuffer.getForDisplay();
        String lastStep = trail.isEmpty() ? "—" : trail.get(trail.size() - 1);

        String browser  = safe(ConfigFactory.getConfig().browser());
        String env      = safe(ConfigFactory.getConfig().env());
        String tcId     = nvl(TestContextManager.getTestCaseId(), "—");
        String execId   = nvl(TestContextManager.getExecutionId(), "—");
        String thread   = Thread.currentThread().getName();

        String appUrl   = "—";
        RuntimeExecutionProfile profile = RuntimeProfileHolder.get();
        if (profile != null && profile.getExecution() != null) {
            appUrl = nvl(profile.getExecution().getApplicationUrl(), "—");
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style='border:2px solid #c62828;border-radius:4px;padding:12px 14px;")
            .append("margin:6px 0 10px 0;background:#fff5f5;font-family:monospace;font-size:12px;'>");

        // ── R-04: color-coded root-cause banner (distinct from the performance
        //         "Root Cause Attribution" diagnostic node) ──
        if (rootCause != null) {
            String rcColor = severityColor(rootCause.severity());
            html.append("<div style='background:").append(rcColor)
                .append(";color:#fff;border-radius:3px;padding:8px 10px;margin-bottom:10px;'>")
                .append("<span style='font-weight:bold;font-size:13px;'>&#9632;&nbsp; FAILURE ROOT CAUSE: ")
                .append(esc(rootCause.category().label())).append("</span>")
                .append("<div style='font-weight:normal;font-size:11px;margin-top:5px;line-height:1.4;'>")
                .append(esc(rootCause.detail())).append("</div></div>");
        }

        html.append("<div style='color:#c62828;font-weight:bold;font-size:13px;margin-bottom:8px;'>")
            .append("&#9888;&nbsp; TEST FAILURE SUMMARY</div>")
            // Data table
            .append("<table style='border-collapse:collapse;width:100%;'>")
            .append(labelRow("Exception",    exType,                            "#c62828", true))
            .append(labelRow("Message",      exMsg,                             "#333",    false))
            .append(labelRow("Failed Step",  lastStep,                          "#333",    false))
            .append(labelRow("Test ID",      tcId,                              "#555",    false))
            .append(labelRow("Execution ID", execId,                            "#888",    false))
            .append(labelRow("Environment",  env + "  |  " + browser + "  |  Thread: " + thread, "#333", false))
            .append(labelRow("App URL",      appUrl,                            "#555",    false));

        if (rootCause != null) {
            html.append(labelRow("Root Cause",  rootCause.category().label(),
                                  severityColor(rootCause.severity()), true))
                .append(labelRow("Confidence",  rootCause.confidence(), "#555", false));
        }

        html.append("</table></div>");

        String htmlStr = html.toString();
        ExtentManager.getExtentTest().info((Markup) () -> htmlStr);
    }

    private static String labelRow(String label, String value,
                                   String valueColor, boolean bold) {
        String b1 = bold ? "<b>" : "";
        String b2 = bold ? "</b>" : "";
        return "<tr>"
            + "<td style='padding:3px 10px 3px 0;color:#777;font-weight:bold;"
            + "white-space:nowrap;vertical-align:top;'>"
            + esc(label) + "</td>"
            + "<td style='padding:3px 0;color:" + valueColor + ";word-break:break-word;'>"
            + b1 + esc(value) + b2 + "</td>"
            + "</tr>";
    }

    /** Maps a report severity to the banner/label color used across the failure summary. */
    private static String severityColor(ReportSeverity severity) {
        if (severity == null) return "#37474f";
        return switch (severity) {
            case CRITICAL -> "#c62828";
            case HIGH     -> "#e65100";
            case MEDIUM   -> "#1565c0";
            default       -> "#37474f";   // LOW / TRACE
        };
    }

    /** Returns the first non-blank line of a multi-line message, truncated to maxLen. */
    private static String firstLine(String s, int maxLen) {
        if (s == null || s.isBlank()) return "—";
        int nl = s.indexOf('\n');
        String line = nl >= 0 ? s.substring(0, nl).stripTrailing() : s;
        return line.length() > maxLen ? line.substring(0, maxLen) + "…" : line;
    }

    private static String nvl(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }

    private static String esc(String input) {
        if (input == null || input.isBlank()) return "—";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
