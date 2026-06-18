package com.gsl.reports;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;

import java.util.List;

/**
 * Renders the "🗄 Database Data Gathering" section into the ExtentReport as a
 * named child node — appearing in the step timeline exactly like "Logs & Diagnostics".
 *
 * <p>The node shows:
 * <ul>
 *   <li>Resolved reference values (owner site, GL, doc scheme, pending SLs, open docs)</li>
 *   <li>Generated test data summary (scenario, sub-ledgers, TDS rows, select documents)</li>
 *   <li>All SQL queries executed during data gathering, each with execution time</li>
 * </ul>
 *
 * <p>Content is populated by {@code DbApVoucherDataSource.buildAndSetGatheringLog()} /
 * {@code DbArVoucherDataSource.loadFreshForScenario()} into the DTO's
 * {@code dbGatheringLog} list. For Excel-mode tests the list is empty and this method
 * is a no-op.
 */
public final class DbGatheringExtentLogger {

    private DbGatheringExtentLogger() {}

    /**
     * AR Voucher overload — renders the gathering node as a step-timeline child node.
     * No-op when {@code data} is null, in Excel mode (dbGatheringLog is empty),
     * or when no ExtentTest is active.
     */
    public static void log(MergedFinanceArVoucherData data) {
        if (data == null) return;
        render(data.getDbGatheringLog());
    }

    /**
     * AP Voucher overload — renders the gathering node as a step-timeline child node.
     * No-op when {@code data} is null, in Excel mode (dbGatheringLog is empty),
     * or when no ExtentTest is active.
     */
    public static void log(MergedFinanceApVoucherData data) {
        if (data == null) return;
        render(data.getDbGatheringLog());
    }

    // ── Shared rendering ─────────────────────────────────────────────────────

    private static void render(List<String> lines) {
        if (lines == null || lines.isEmpty()) return;
        if (!ExtentManager.hasTest()) return;

        // Split: SQL lines (start with "[DB Query") go to technical node; the rest to business node.
        java.util.List<String> businessLines = new java.util.ArrayList<>();
        java.util.List<String> sqlLines      = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line != null && line.startsWith("[DB Query")) sqlLines.add(line);
            else businessLines.add(line);
        }

        if (!businessLines.isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("📋 Test Data Resolution", buildPre(businessLines));
        }
        if (!sqlLines.isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("🔍 SQL Queries Executed", buildPre(sqlLines));
        }
    }

    private static String buildPre(java.util.List<String> lines) {
        StringBuilder html = new StringBuilder();
        html.append("<pre style='font-size:11px;font-family:monospace;margin:0;white-space:pre-wrap;")
            .append("background:#f8fbff;padding:8px;border-left:3px solid #6aacf0;line-height:1.5;'>");
        for (String line : lines) html.append(escHtml(line)).append("\n");
        html.append("</pre>");
        return html.toString();
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
