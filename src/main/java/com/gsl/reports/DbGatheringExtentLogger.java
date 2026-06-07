package com.gsl.reports;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;

import java.util.List;

/**
 * P3: Renders the "Database Data Gathering" section into the ExtentReport.
 *
 * <p>This section is inserted BEFORE the "Test Data" tables (ArVoucherExtentDescriptionLogger)
 * and BEFORE the first step node, giving it the same visual prominence as "Login to Application".
 *
 * <p>The section shows:
 * <ul>
 *   <li>The SQL queries / resolution logic used to discover test data from the live DB</li>
 *   <li>Resolved reference values (owner site, GL, doc scheme, pending SLs, open docs)</li>
 * </ul>
 *
 * <p>Content is populated by {@code DbArVoucherDataSource.loadFreshForScenario()} into
 * {@link MergedFinanceArVoucherData#getDbGatheringLog()}.
 * For Excel-mode tests the list will be empty and this method is a no-op.
 */
public final class DbGatheringExtentLogger {

    private DbGatheringExtentLogger() {}

    /**
     * Logs the DB data-gathering section to the current ExtentTest node.
     * No-op if {@code data} is null, in Excel mode (dbGatheringLog is empty),
     * or if no ExtentTest is currently active.
     */
    public static void log(MergedFinanceArVoucherData data) {
        if (data == null) return;
        List<String> lines = data.getDbGatheringLog();
        if (lines == null || lines.isEmpty()) return;
        if (!ExtentManager.hasTest()) return;

        StringBuilder html = new StringBuilder();
        html.append("<details style='margin:4px 0;border:1px solid #d0e8ff;border-radius:3px;'>")
            .append("<summary style='cursor:pointer;font-weight:bold;font-size:12px;")
            .append("padding:5px 8px;background:#eaf4ff;color:#1a4d80;'>")
            .append("&#x1F5C4; Database Data Gathering")
            .append("</summary>")
            .append("<div style='padding:6px 8px;'>")
            .append("<pre style='font-size:11px;font-family:monospace;margin:0;white-space:pre-wrap;")
            .append("background:#f8fbff;padding:8px;border-left:3px solid #6aacf0;line-height:1.5;'>");

        for (String line : lines) {
            html.append(escHtml(line)).append("\n");
        }

        html.append("</pre></div></details>");

        ExtentLogger.infoHtml(html.toString());
    }

    /**
     * AP Voucher overload — same rendering logic as the AR version.
     * No-op when {@code data} is null, in Excel mode (dbGatheringLog is empty),
     * or when no ExtentTest is active.
     */
    public static void log(MergedFinanceApVoucherData data) {
        if (data == null) return;
        List<String> lines = data.getDbGatheringLog();
        if (lines == null || lines.isEmpty()) return;
        if (!ExtentManager.hasTest()) return;

        StringBuilder html = new StringBuilder();
        html.append("<details style='margin:4px 0;border:1px solid #d0e8ff;border-radius:3px;'>")
            .append("<summary style='cursor:pointer;font-weight:bold;font-size:12px;")
            .append("padding:5px 8px;background:#eaf4ff;color:#1a4d80;'>")
            .append("&#x1F5C4; Database Data Gathering (AP Voucher)")
            .append("</summary>")
            .append("<div style='padding:6px 8px;'>")
            .append("<pre style='font-size:11px;font-family:monospace;margin:0;white-space:pre-wrap;")
            .append("background:#f8fbff;padding:8px;border-left:3px solid #6aacf0;line-height:1.5;'>");

        for (String line : lines) {
            html.append(escHtml(line)).append("\n");
        }

        html.append("</pre></div></details>");

        ExtentLogger.infoHtml(html.toString());
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
