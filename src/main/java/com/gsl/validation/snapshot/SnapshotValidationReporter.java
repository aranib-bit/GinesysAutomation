package com.gsl.validation.snapshot;

import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.reports.DatabaseLogger;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.FoldableSectionRenderer;
import com.gsl.reports.FoldableSectionRenderer.Status;
import com.gsl.utils.Log;
import com.gsl.utils.io.XlsxWriter;
import com.gsl.validation.snapshot.audit.AuditFieldCheckResult;
import com.gsl.validation.snapshot.report.ChangeClassification;
import com.gsl.validation.snapshot.report.ChangeClassifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Renders a {@link DiffResult} as Extent tables, restructured per
 * {@code Database_Snapshot_Validation_Representation.md}'s summary-first, exception-first
 * hierarchy ("Do not make the user read the database diff to understand the test result"):
 * <ol>
 *   <li>Table 1 "Validation Summary" — tables/rows/columns checked, passed/failed, overall result.
 *       Moved to the TOP (was last) so the headline result is visible before any detail.</li>
 *   <li>Table 2 "Exceptions" — every failing check, table+row+column+expected+actual+message, one
 *       place to look first — only rendered when at least one failure exists.</li>
 *   <li>Table 3 "Change Classification Summary" — counts per {@link ChangeClassification}
 *       (USER/SYSTEM/CASCADE/AUDIT/IGNORED/UNEXPECTED), doc §10's grouping, derived from the same
 *       {@link ColumnCheckResult}s already computed — never a second classification pass.</li>
 *   <li>Table 4 "Audit Trail" — row-count existence check per audit table (now with the paired
 *       data table's outcome baked into the expectation, and trigger status), plus a field-level
 *       breakdown table when {@link DiffResult#auditFieldChecks()} is non-empty.</li>
 *   <li>Table 5 "Full Validation Details" — the original flat per-column table, unchanged content,
 *       just relabeled and moved last (doc §20: "the current table should not be removed... it
 *       becomes the Full Details/Raw Validation section").</li>
 * </ol>
 * Wrapped in {@link DatabaseLogger#validationStart}/{@link DatabaseLogger#validationComplete};
 * every individual failure additionally gets its own {@link DatabaseLogger#checkFailed} block.
 */
public final class SnapshotValidationReporter {

    private SnapshotValidationReporter() {}

    // Plan §13: Status/Result columns widened (8%→12%, 12%→14%) as a safety margin against
    // badge-pill overflow under table-layout:fixed — redistributed from neighboring columns so
    // each array still sums to 100%. Not load-bearing on its own; the real fix is tableHtml's new
    // white-space:nowrap/vertical-align:top (FoldableSectionRenderer.java), which is what actually
    // prevents the header-wrap/overflow bugs regardless of the exact width split.
    private static final String[] WIDTHS_6COL_WITH_STATUS =
            {"11%", "18%", "13%", "15%", "15%", "16%", "12%"};
    private static final String[] WIDTHS_7COL_RESULT =
            {"10%", "17%", "12%", "13%", "16%", "16%", "16%"};
    private static final String[] WIDTHS_AUDIT_FIELD =
            {"11%", "16%", "10%", "11%", "13%", "13%", "13%", "13%", "8%"};

    public static void render(String label, DiffResult result) {
        DatabaseLogger.validationStart(label);

        // Foldable formatting (plan §4I, icons/badges/widths/summaries added plan §10) — same
        // <details>/rs-sub/rs-chev shape ActualResultsLogger uses for "Actual Results", via the
        // shared FoldableSectionRenderer. Every buildXxxTable(...) method below still owns the
        // exact same data/content as before — only the wrapping HTML/icon/width/badge presentation
        // changes here. Summary/Exceptions/Known-Issues open by default (the headline sections);
        // the rest collapsed.
        //
        // Each section is rendered via infoHtmlOnChildNode (its own dedicated node/log-table)
        // rather than a bare infoHtml call on this shared "Database Snapshot Validation" step
        // node — up to 6 infoHtml calls sharing ONE log table meant FoldableSectionRenderer's
        // column-hiding CSS only hid columns on the matching row while sibling rows still claimed
        // the table's width, collapsing each widget's surviving cell into the narrow status-badge
        // column and garbling its content into vertical, one-character-per-line text. Mirrors the
        // proven fix already applied to DbGatheringExtentLogger's "Test Data Resolution"/"SQL
        // Queries Executed" nodes.
        ExtentLogger.infoHtmlOnChildNode("Validation Summary", FoldableSectionRenderer.foldableTable(
                FoldableSectionRenderer.ICO_MONITOR, "Validation Summary", true, buildSummaryTable(result)));

        if (!result.failures().isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("Exceptions", FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_WARNING, "❌ Exceptions", true,
                    result.failures().size() + " exception(s) found",
                    buildExceptionsTable(result.failures()), WIDTHS_6COL_WITH_STATUS));
        }

        List<ColumnCheckResult> knownIssues = result.columnChecks().stream()
                .filter(c -> c.knownIssueNote() != null)
                .toList();
        if (!knownIssues.isEmpty()) {
            ExtentLogger.infoHtmlOnChildNode("Known Issues", FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_WARNING, "⚠️ Known Issues", true,
                    knownIssues.size() + " known issue(s) — visible, non-blocking",
                    buildKnownIssuesTable(knownIssues), WIDTHS_6COL_WITH_STATUS));
        }

        ExtentLogger.infoHtmlOnChildNode("Change Classification Summary", FoldableSectionRenderer.foldableTable(
                FoldableSectionRenderer.ICO_TABLE, "Change Classification Summary", false,
                buildClassificationSummaryTable(result)));

        if (!result.auditChecks().isEmpty()) {
            long auditPassed = result.auditChecks().stream().filter(AuditTableCheckResult::passed).count();
            ExtentLogger.infoHtmlOnChildNode("Audit Trail", FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_DB, "Audit Trail", false,
                    result.auditChecks().size() + " audit table(s) checked, " + auditPassed + " passed",
                    buildAuditTable(result.auditChecks()), null));
        }
        if (!result.auditFieldChecks().isEmpty()) {
            long fieldPassed = result.auditFieldChecks().stream().filter(AuditFieldCheckResult::passed).count();
            ExtentLogger.infoHtmlOnChildNode("Audit Trail — Field-Level Detail", FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_DB, "Audit Trail — Field-Level Detail", false,
                    result.auditFieldChecks().size() + " audit field(s) checked, " + fieldPassed + " passed",
                    buildAuditFieldTable(result.auditFieldChecks()), WIDTHS_AUDIT_FIELD));
        }

        // Plan §11: the visible table below still suppresses genuinely-unchanged MUST_NOT_CHANGE
        // columns (real diagnostic-noise reduction) — but that suppression used to be silent,
        // leaving the Summary table's "Columns Checked"/"Passed" counts (computed from the
        // UNFILTERED list) disagreeing with this table's visible row count with no explanation.
        // Made explicit here instead, and (when the config flag below is on) backed by a full,
        // unfiltered .xlsx export so a suppressed-but-checked column can still be looked up.
        List<ColumnCheckResult> allChecks = result.columnChecks();
        String[][] fullTable = buildColumnDiffTable(allChecks);
        int visibleRows = fullTable.length - 1;
        int suppressed = allChecks.size() - visibleRows;
        boolean xlsxEnabled = ConfigFactory.getReportConfig().fullValidationXlsxEnabled();
        String xlsxLink = xlsxEnabled ? writeFullValidationXlsx(label, allChecks) : null;
        String fullDetailsSummary = visibleRows + " of " + allChecks.size() + " columns shown"
                + (suppressed > 0 ? " — " + suppressed + " unchanged MUST_NOT_CHANGE column(s) suppressed" : "")
                + (xlsxLink != null
                        ? "; full set: <a href='" + xlsxLink + "'>Download full validation data (.xlsx)</a>"
                        : "");
        ExtentLogger.infoHtmlOnChildNode("Full Validation Details", FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_TABLE, "Full Validation Details", false,
                fullDetailsSummary, fullTable, WIDTHS_7COL_RESULT));

        for (ValidationFailure f : result.failures()) {
            DatabaseLogger.checkFailed(
                    "[" + f.table() + "] " + f.businessKey() + (f.column() != null ? " ." + f.column() : ""),
                    f.expected(), f.actual(), "(see BEFORE/AFTER snapshot captures above)", f.businessKey(), 0, 1);
        }

        DatabaseLogger.validationComplete(label, result.failures().isEmpty());
    }

    /**
     * Plan §11: writes EVERY checked column (not just the visible/suppressed-filtered subset) to
     * a {@code .xlsx} via the reusable {@link XlsxWriter}, under a {@code snapshots/} directory
     * sibling to the existing {@code screenshots/} convention {@code ScreenshotUtils} already
     * establishes under the run's report directory. Returns the relative link path for the
     * caller to embed (relative to the report's own {@code index.html}, same assumption
     * {@code screenshots/} already relies on), or {@code null} if the write failed (logged, not
     * thrown — a failed export must not fail the actual validation).
     */
    private static String writeFullValidationXlsx(String label, List<ColumnCheckResult> allChecks) {
        String safeLabel = (label == null ? "validation" : label).replaceAll("[^A-Za-z0-9_-]", "_");
        if (safeLabel.length() > 60) safeLabel = safeLabel.substring(0, 60);
        String fileName = safeLabel + "_" + System.currentTimeMillis() + "_full-validation.xlsx";

        String[] header = {"Table", "Row (Business Key)", "Column", "Expectation", "Before", "After", "Status"};
        List<String[]> rows = new ArrayList<>();
        for (ColumnCheckResult c : allChecks) {
            String status = c.knownIssueNote() != null ? "KNOWN_ISSUE" : c.passed() ? "PASS" : "FAIL";
            rows.add(new String[]{
                    c.table(), c.businessKey(), c.column(), c.expectationLabel(),
                    c.beforeDisplay(), c.afterDisplay(), status
            });
        }

        try {
            Path dir = Paths.get(FrameworkConstants.getRunReportDir(), "snapshots");
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            XlsxWriter.write(file, "Full Validation Details", header, rows);
            return "snapshots/" + fileName;
        } catch (IOException e) {
            Log.warn("[SnapshotValidationReporter] Failed to write full-validation .xlsx for '{}': {}",
                    label, e.getMessage());
            return null;
        }
    }

    private static String[][] buildExceptionsTable(List<ValidationFailure> failures) {
        String[][] table = new String[failures.size() + 1][7];
        table[0] = new String[]{"Table", "Row (Business Key)", "Column", "Expected", "Actual", "Message", "Status"};
        for (int i = 0; i < failures.size(); i++) {
            ValidationFailure f = failures.get(i);
            table[i + 1] = redRow(new String[]{
                    f.table(), f.businessKey(), f.column() == null ? "—" : f.column(),
                    f.expected(), f.actual(), f.message(), FoldableSectionRenderer.badge(Status.FAIL)
            });
        }
        return table;
    }

    /** Same shape as {@link #buildExceptionsTable} but for known-issue mismatches — a confirmed,
     *  already-tracked defect that does not fail the test (see {@link ColumnRule#knownIssue}) but
     *  must stay just as visible in the report as a real failure would be. */
    private static String[][] buildKnownIssuesTable(List<ColumnCheckResult> knownIssues) {
        String[][] table = new String[knownIssues.size() + 1][7];
        table[0] = new String[]{"Table", "Row (Business Key)", "Column", "Before", "After", "Note", "Status"};
        for (int i = 0; i < knownIssues.size(); i++) {
            ColumnCheckResult c = knownIssues.get(i);
            table[i + 1] = amberRow(new String[]{
                    c.table(), c.businessKey(), c.column() == null ? "—" : c.column(),
                    c.beforeDisplay(), c.afterDisplay(), c.knownIssueNote(),
                    FoldableSectionRenderer.badge(Status.KNOWN_ISSUE)
            });
        }
        return table;
    }

    private static String[][] buildClassificationSummaryTable(DiffResult result) {
        Map<ChangeClassification, int[]> counts = new LinkedHashMap<>(); // [passed, failed]
        for (ChangeClassification c : ChangeClassification.values()) counts.put(c, new int[2]);

        for (ColumnCheckResult check : result.columnChecks()) {
            if ("<row>".equals(check.column()) && !check.passed()) continue; // structural failures already in Exceptions
            ChangeClassification classification = ChangeClassifier.classify(check, null);
            int[] tally = counts.get(classification);
            tally[check.passed() ? 0 : 1]++;
        }

        String[][] table = new String[ChangeClassification.values().length + 1][3];
        table[0] = new String[]{"Classification", "Passed", "Failed"};
        int i = 1;
        for (Map.Entry<ChangeClassification, int[]> e : counts.entrySet()) {
            table[i++] = new String[]{e.getKey().name(), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1])};
        }
        return table;
    }

    private static String[][] buildColumnDiffTable(List<ColumnCheckResult> checks) {
        // Suppress no-op MUST_NOT_CHANGE rows (Before == After) — they carry zero diagnostic
        // value and only bury the columns that actually changed or failed.
        List<ColumnCheckResult> visible = checks.stream()
                .filter(c -> !(c.expectationLabel().equals("MUST_NOT_CHANGE")
                        && Objects.equals(c.beforeDisplay(), c.afterDisplay())))
                .toList();

        String[][] table = new String[visible.size() + 1][7];
        table[0] = new String[]{"Table", "Row (Business Key)", "Column", "Expectation", "Before", "After", "Result"};
        for (int i = 0; i < visible.size(); i++) {
            ColumnCheckResult c = visible.get(i);
            // A known-issue mismatch is technically `passed()` (it never fails the test) but must
            // NOT render indistinguishably from a clean PASS — a distinct badge keeps it visible
            // as the confirmed, already-tracked defect it is.
            String result = FoldableSectionRenderer.badge(
                    c.knownIssueNote() != null ? Status.KNOWN_ISSUE : c.passed() ? Status.PASS : Status.FAIL);
            String[] row = {
                    c.table(), c.businessKey(), c.column(), c.expectationLabel(),
                    c.beforeDisplay(), c.afterDisplay(), result
            };
            table[i + 1] = c.knownIssueNote() != null ? amberRow(row) : c.passed() ? row : redRow(row);
        }
        return table;
    }

    /** Wraps a failed row's cells in red text so failures stand out from passing rows. */
    private static String[] redRow(String[] row) {
        return styledRow(row, "#b71c1c");
    }

    /** Wraps a known-issue row's cells in amber text — mismatched but a confirmed, already-tracked
     *  defect that intentionally does not fail the test (see {@link ColumnRule#knownIssue}). */
    private static String[] amberRow(String[] row) {
        return styledRow(row, "#e65100");
    }

    private static String[] styledRow(String[] row, String color) {
        String[] styled = new String[row.length];
        for (int j = 0; j < row.length; j++) {
            styled[j] = row[j] == null ? null : "<span style='color:" + color + "'>" + row[j] + "</span>";
        }
        return styled;
    }

    private static String[][] buildAuditTable(List<AuditTableCheckResult> checks) {
        String[][] table = new String[checks.size() + 1][6];
        table[0] = new String[]{"Audit Table", "Rows Before", "Rows After", "Trigger", "Expected", "Result"};
        for (int i = 0; i < checks.size(); i++) {
            AuditTableCheckResult a = checks.get(i);
            String[] row = {
                    a.auditTable(), String.valueOf(a.rowsBefore()), String.valueOf(a.rowsAfter()),
                    a.triggerStatus(), a.expectedLabel(),
                    FoldableSectionRenderer.badge(a.passed() ? Status.PASS : Status.FAIL)
            };
            table[i + 1] = a.passed() ? row : redRow(row);
        }
        return table;
    }

    private static String[][] buildAuditFieldTable(List<AuditFieldCheckResult> fieldChecks) {
        String[][] table = new String[fieldChecks.size() + 1][9];
        table[0] = new String[]{"Table", "Row (Business Key)", "Operation", "Column",
                "Expected Old", "Expected New", "Actual Old", "Actual New", "Status"};
        for (int i = 0; i < fieldChecks.size(); i++) {
            AuditFieldCheckResult f = fieldChecks.get(i);
            String[] row = {
                    f.table(), f.recordId(), f.operation() == null ? "—" : f.operation().name(),
                    f.column() == null ? "—" : f.column(),
                    f.expectedOld(), f.expectedNew(), f.actualOld(), f.actualNew(),
                    FoldableSectionRenderer.badge(f.passed() ? Status.PASS : Status.FAIL)
            };
            table[i + 1] = f.passed() ? row : redRow(row);
        }
        return table;
    }

    private static String[][] buildSummaryTable(DiffResult result) {
        int totalColumns = result.columnChecks().size();
        long passed = result.columnChecks().stream().filter(ColumnCheckResult::passed).count();
        long failed = totalColumns - passed;
        long critical = result.failures().stream()
                .filter(f -> f.severity() == ValidationFailure.Severity.CRITICAL).count();
        long error = result.failures().stream()
                .filter(f -> f.severity() == ValidationFailure.Severity.ERROR).count();
        long knownIssues = result.columnChecks().stream().filter(c -> c.knownIssueNote() != null).count();

        Set<String> tablesChecked = new LinkedHashSet<>();
        result.columnChecks().forEach(c -> tablesChecked.add(c.table()));
        result.auditChecks().forEach(a -> tablesChecked.add(a.auditTable()));

        long auditFieldPassed = result.auditFieldChecks().stream().filter(AuditFieldCheckResult::passed).count();
        long auditFieldFailed = result.auditFieldChecks().size() - auditFieldPassed;

        return new String[][]{
                {"Metric", "Value"},
                {"Overall Result", result.failures().isEmpty() ? "✓ PASS" : "✗ FAIL"},
                {"Tables Checked", String.valueOf(tablesChecked.size())},
                {"Columns Checked", String.valueOf(totalColumns)},
                {"Passed", String.valueOf(passed)},
                {"Failed", String.valueOf(failed)},
                {"Audit Fields Checked", String.valueOf(result.auditFieldChecks().size())},
                {"Audit Fields Passed", String.valueOf(auditFieldPassed)},
                {"Audit Fields Failed", String.valueOf(auditFieldFailed)},
                {"Critical", String.valueOf(critical)},
                {"Error", String.valueOf(error)},
                {"Known Issues", String.valueOf(knownIssues)},
        };
    }
}
