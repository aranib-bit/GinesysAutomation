package com.gsl.reports;

import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

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

    /**
     * General Voucher overload — renders the gathering node as a step-timeline child node.
     * No-op when {@code data} is null, in Excel mode (dbGatheringLog is empty),
     * or when no ExtentTest is active. Content is populated by
     * {@code DbGeneralVoucherDataSource.load()} into {@code dbGatheringLog} — previously
     * collected but never rendered, since no overload existed for this type.
     */
    public static void log(MergedFinanceGeneralVoucherData data) {
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

        // Foldable formatting (plan §4I/Point 10), STILL via infoHtmlOnChildNode — plan §5 (fix
        // for a live-confirmed 4I regression): a bare infoHtml(...) call here (this method fires
        // before any StepExecutor.step() exists, per this class's own Javadoc) attaches to the
        // ROOT TEST's own shared log table, alongside 40+ other real rows whose Status/Timestamp
        // cells anchor those columns to their thead-declared 70px/110px widths. FoldableSectionRenderer
        // .CSS's tr:has(.rs-wrap)>td:not(:last-child){display:none} rule then reassigns the lone
        // remaining cell to an earlier column slot under table-layout:auto, inheriting THAT slot's
        // narrow width — confirmed live (browser-rendered DOM measurement) to wrap the section
        // title character-by-character. ActualResultsLogger/SnapshotValidationReporter never hit
        // this because they always run inside their OWN dedicated StepExecutor.step(...) node,
        // whose table has only their one widget row — no other row to anchor a competing width.
        // infoHtmlOnChildNode restores that same dedicated-node/single-row-table context here,
        // while still using FoldableSectionRenderer's HTML for the actual styling-consistency win
        // 4I was after.
        if (!businessLines.isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("📋 Test Data Resolution", FoldableSectionRenderer.foldableSection(
                    "📋", "Test Data Resolution", false, buildPre(businessLines)));
        }
        if (!sqlLines.isEmpty()) {
            // Same SQL/Time table shape as ActualResultsLogger's "DB Lookups" section — both
            // consume the identical "[DB Query ⏱ Nms] SQL..." string format, so this is a real
            // dedup, not just a styling change, and gives these queries a proper table instead of
            // a <pre> dump.
            ExtentLogger.infoHtmlOnChildNode("🔍 SQL Queries Executed", FoldableSectionRenderer.foldableSection(
                    "🔍", "SQL Queries Executed", false, FoldableSectionRenderer.sqlQueriesTableHtml(sqlLines)));
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
