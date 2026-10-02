package com.gsl.validation.snapshot;

import com.gsl.config.ConfigFactory;
import com.gsl.constants.FrameworkConstants;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.adm.AdmDocSchemeDao;
import com.gsl.database.dao.adm.AdmYearDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.query.AdmOuQuery;
import com.gsl.database.query.AdmSiteQuery;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherReleaseStatus;
import com.gsl.enums.VoucherType;
import com.gsl.model.adm.AdmYear;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.reports.DatabaseLogger;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.FoldableSectionRenderer;
import com.gsl.reports.FoldableSectionRenderer.Status;
import com.gsl.reports.VoucherReportUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // Table | Row | Column | Expectation | Detail | Before | After | Classification | Result —
    // same Detail/Classification additions as the collapsed shape below, for the mixed
    // (Edit/Revert) tables.
    private static final String[] WIDTHS_9COL_RESULT =
            {"8%", "14%", "9%", "11%", "14%", "11%", "11%", "13%", "9%"};
    private static final String[] WIDTHS_AUDIT_FIELD =
            {"11%", "16%", "10%", "11%", "13%", "13%", "13%", "13%", "8%"};
    // Mode-aware collapses of the above — see resolveAuditFieldShape/buildAuditFieldTable.
    // ROW_LEVEL: every check is a row-level INSERT/DELETE with no column data at all (Create's
    // usual case) — Table | Row | Operation | Status, nothing else ever carries information.
    private static final String[] WIDTHS_AUDIT_FIELD_ROW_LEVEL =
            {"20%", "40%", "20%", "20%"};
    // INSERT_ONLY/DELETE_ONLY: some checks DO carry column data (an unasserted cascade column),
    // but only one direction is ever meaningful — Table | Row | Column | Value | Status.
    private static final String[] WIDTHS_AUDIT_FIELD_COLLAPSED =
            {"12%", "28%", "16%", "30%", "14%"};
    // Item T: collapsed insert-only/delete-only per-table shape — Row | Column | Value | Detail |
    // Classification | Result. Classification added per explicit request: a reader shouldn't have
    // to cross-reference "Change Classification Summary" separately to know whether a given row
    // is a USER_CHANGE/SYSTEM_CHANGE/CASCADE_CHANGE/etc. Detail (also per explicit request) states
    // WHY the check exists in plain words (Mandatory / From UI / System-generated / etc.).
    private static final String[] WIDTHS_6COL_RESULT =
            {"18%", "16%", "24%", "18%", "14%", "10%"};

    /**
     * Renders one {@link DiffResult} to the Extent report as the summary-first, exception-first
     * table sequence described in this class's Javadoc, and mirrors every failure into
     * {@link DatabaseLogger#checkFailed}. Does not itself assert anything — {@code result}'s
     * failures are the caller's ({@link VoucherSnapshotValidator}'s) responsibility to stage/assert.
     *
     * @param label unique-per-invocation label (already embeds the document number) used to build
     *              this call's own anchor ids and, when enabled, its full-validation {@code .xlsx} filename
     */
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

        // Item F: stable, application-controlled anchor ids for this specific validation call
        // (label already embeds the document number, so it's unique per invocation) — targets for
        // the Validation Summary's per-metric hyperlinks below.
        String anchorBase = sanitizeForId(label);
        String detailsAnchorId = "svr-details-" + anchorBase;
        String exceptionsAnchorId = "svr-exceptions-" + anchorBase;
        String auditAnchorId = "svr-audit-" + anchorBase;
        boolean hasExceptions = !result.failures().isEmpty();
        boolean hasAuditFieldDetail = !result.auditFieldChecks().isEmpty();

        // Item E: reformatted to match ActualResultsLogger's own "Validation Summary" shape (the
        // shared STATUS_LEGEND + a compact horizontal line) instead of a differently-styled
        // vertical Metric/Value table — plus Item F's per-metric hyperlinks.
        ExtentLogger.infoHtmlOnChildNode("Validation Summary", buildSummaryHtml(result,
                detailsAnchorId, exceptionsAnchorId, auditAnchorId, hasExceptions, hasAuditFieldDetail));

        if (hasExceptions) {
            // Item Y: id now goes directly on the <details> element (see the id-overload below)
            // instead of a separate preceding <a id> marker — see scrollLink's own Javadoc.
            ExtentLogger.infoHtmlOnChildNode("Exceptions",
                    FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_WARNING, "❌ Exceptions", true,
                    result.failures().size() + " exception(s) found",
                    buildExceptionsTable(result.failures()), WIDTHS_6COL_WITH_STATUS, exceptionsAnchorId));
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
        if (hasAuditFieldDetail) {
            List<AuditFieldCheckResult> auditFieldChecks = result.auditFieldChecks();
            long fieldPassed = auditFieldChecks.stream().filter(AuditFieldCheckResult::passed).count();
            // Recurring gap, now fixed: this table used to always render the full 9-column
            // Old/New shape regardless of mode — for Create (every row a row-level INSERT with no
            // column data at all), every Column/Expected Old/Expected New/Actual Old/Actual New
            // cell was permanently "—", exactly like "Full Validation Details" before Item T's fix.
            // Same mode-aware collapse principle applied here now — see resolveAuditFieldShape.
            AuditFieldShape shape = resolveAuditFieldShape(auditFieldChecks);
            String[] widths = switch (shape) {
                case ROW_LEVEL -> WIDTHS_AUDIT_FIELD_ROW_LEVEL;
                case INSERT_ONLY, DELETE_ONLY -> WIDTHS_AUDIT_FIELD_COLLAPSED;
                case FULL -> WIDTHS_AUDIT_FIELD;
            };
            // Item Y: id on the <details> itself, same as Exceptions above.
            ExtentLogger.infoHtmlOnChildNode("Audit Trail — Field-Level Detail",
                    FoldableSectionRenderer.foldableTableWithSummary(FoldableSectionRenderer.ICO_DB, "Audit Trail — Field-Level Detail", false,
                    auditFieldChecks.size() + " audit field(s) checked, " + fieldPassed + " passed",
                    buildAuditFieldTable(auditFieldChecks, shape), widths, auditAnchorId));
        }

        // Plan §11: still suppresses genuinely-unchanged MUST_NOT_CHANGE columns (real
        // diagnostic-noise reduction, config-gated per Item A) — but that suppression used to be
        // silent, leaving the Summary's "Columns Checked"/"Passed" counts (computed from the
        // UNFILTERED list) disagreeing with this table's visible row count with no explanation.
        // Made explicit here instead, and (when the config flag below is on) backed by a full,
        // unfiltered .xlsx export so a suppressed-but-checked column can still be looked up.
        //
        // Item E: grouped per table (Column | Expected | Actual | Status shape per group) instead
        // of one flat, every-table-interleaved 7-column table — matches ActualResultsLogger's own
        // "Actual Results" per-table sub-section style.
        List<ColumnCheckResult> allChecks = result.columnChecks();
        boolean suppressUnchanged = ConfigFactory.getReportConfig().suppressUnchangedColumnsEnabled();
        List<ColumnCheckResult> visible = allChecks.stream()
                .filter(c -> !(suppressUnchanged && c.expectationLabel().equals("MUST_NOT_CHANGE")
                        && Objects.equals(c.beforeDisplay(), c.afterDisplay())))
                .toList();
        int suppressed = allChecks.size() - visible.size();
        boolean xlsxEnabled = ConfigFactory.getReportConfig().fullValidationXlsxEnabled();
        String xlsxLink = xlsxEnabled ? writeFullValidationXlsx(label, allChecks) : null;
        // Round 23: named explicitly ("Columns Checked" / "Columns Shown") instead of the old
        // ambiguous "X of Y columns shown" phrasing — a reader couldn't tell at a glance that the
        // LARGER number (allChecks.size()) means every column was actually checked in the
        // background, not just that some arbitrary total existed; only the smaller number
        // (visible.size()) is affected by suppression.
        String fullDetailsSummary = "Columns Checked: " + allChecks.size()
                + " | Columns Shown: " + visible.size()
                + (suppressed > 0 ? " (" + suppressed + " unchanged MUST_NOT_CHANGE column(s) suppressed)" : "")
                + (xlsxLink != null
                        ? "; full set: <a href='" + xlsxLink + "'>Download full validation data (.xlsx)</a>"
                        : "");
        // Item Y: id on the <details> itself, same as Exceptions/Audit Trail — Field-Level Detail above.
        String fullDetailsHtml = FoldableSectionRenderer.foldableSection(FoldableSectionRenderer.ICO_TABLE, "Full Validation Details", false,
                        "<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>" + fullDetailsSummary + "</div>"
                                + buildGroupedFullDetailsHtml(visible), detailsAnchorId);
        ExtentLogger.infoHtmlOnChildNode("Full Validation Details", fullDetailsHtml);

        for (ValidationFailure f : result.failures()) {
            DatabaseLogger.checkFailed(
                    "[" + f.table() + "] " + f.businessKey() + (f.column() != null ? " ." + f.column() : ""),
                    f.expected(), f.actual(), "(see BEFORE/AFTER snapshot captures above)", f.businessKey(), 0, 1);
        }

        DatabaseLogger.validationComplete(label, result.failures().isEmpty());
    }

    /** Item F: sanitizes a validation {@code label} into an id-safe token for the anchor markers
     *  above — same character-class restriction {@link #writeFullValidationXlsx} already applies
     *  for its own filename, kept as an independent copy here rather than sharing a helper to
     *  avoid coupling two genuinely different concerns (a DOM id vs. a filesystem-safe name). */
    private static String sanitizeForId(String label) {
        // Item R fix: NO length cap — a 40-char truncation used to cut off the document-number
        // suffix (the only part that makes one invocation's anchor differ from a sibling's),
        // making every Create invocation in a suite run share one id and breaking the Validation
        // Summary hyperlinks (confirmed live: duplicate ids collide, document.querySelector('#id')
        // always resolves to the first match). Labels here are ~60-80 chars — nowhere near a real
        // HTML id-length concern — so there's no reason to truncate at all.
        return (label == null ? "validation" : label).replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /**
     * Item E: the reformatted "Validation Summary" — {@link FoldableSectionRenderer#STATUS_LEGEND}
     * plus a compact horizontal line, matching {@code ActualResultsLogger}'s own "Validation
     * Summary" shape instead of the previous vertical Metric/Value table. Item F: each count is a
     * clickable {@link FoldableSectionRenderer#scrollLink} to the section it actually counts —
     * only when that target section will actually render (a link to an empty/never-rendered
     * section would silently no-op), otherwise plain text.
     */
    private static String buildSummaryHtml(DiffResult result, String detailsAnchorId,
            String exceptionsAnchorId, String auditAnchorId, boolean hasExceptions, boolean hasAuditFieldDetail) {
        int totalColumns = result.columnChecks().size();
        long passed = result.columnChecks().stream().filter(ColumnCheckResult::passed).count();
        long failed = totalColumns - passed;
        boolean overallPass = result.failures().isEmpty();

        Set<String> tablesChecked = new LinkedHashSet<>();
        result.columnChecks().forEach(c -> tablesChecked.add(c.table()));
        result.auditChecks().forEach(a -> tablesChecked.add(a.auditTable()));

        String tablesValue = FoldableSectionRenderer.scrollLink(detailsAnchorId, String.valueOf(tablesChecked.size()));
        String columnsValue = FoldableSectionRenderer.scrollLink(detailsAnchorId, String.valueOf(totalColumns));
        String passedValue = FoldableSectionRenderer.scrollLink(detailsAnchorId, String.valueOf(passed));
        String failedValue = hasExceptions
                ? FoldableSectionRenderer.scrollLink(exceptionsAnchorId, String.valueOf(failed))
                : String.valueOf(failed);
        String auditValue = hasAuditFieldDetail
                ? FoldableSectionRenderer.scrollLink(auditAnchorId, String.valueOf(result.auditFieldChecks().size()))
                : String.valueOf(result.auditFieldChecks().size());

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.STATUS_LEGEND);
        sb.append("<div style='padding:10px 14px;margin-bottom:8px;border-radius:8px;")
          .append("border:0.5px solid #E2E8F0;background:#F8FAFC'>");
        sb.append("<div style='font-size:11px;font-weight:600;color:#64748B;text-transform:uppercase;")
          .append("letter-spacing:.05em;margin-bottom:6px'>Validation Summary</div>");
        // Item Q: 13px to match DocumentMessageBox's "VERIFY SAVE RESULT" box.
        sb.append("<div style='display:flex;gap:18px;flex-wrap:wrap;align-items:center;font-size:13px'>");
        sb.append("<span>Tables Checked: <b>").append(tablesValue).append("</b></span>");
        sb.append("<span>Columns Checked: <b>").append(columnsValue).append("</b></span>");
        sb.append("<span style='color:#27500A'>Passed: <b>").append(passedValue).append("</b></span>");
        sb.append("<span style='color:#991B1B'>Failed: <b>").append(failedValue).append("</b></span>");
        sb.append("<span>Audit Fields Checked: <b>").append(auditValue).append("</b></span>");
        sb.append("<span>Overall Result: ")
          .append(FoldableSectionRenderer.badge(overallPass ? Status.PASS : Status.FAIL)).append("</span>");
        sb.append("</div></div>");
        return sb.toString();
    }

    /** Per explicit request: a compact summary rendered right in a per-table section's own
     *  {@code <summary>} line (visible even collapsed, same idea as "Validation Summary" above) —
     *  a count per {@link #friendlyExpectationDetail} category ("N Mandatory (...)", "N From UI
     *  (user-supplied)", "N System-generated", etc. — whatever categories this table's checks
     *  actually contain, not a fixed set of 3) plus the same PASS/FAIL badge every other
     *  Result/Status column already uses. The row-level {@code <row>} INSERT/DELETE check is
     *  excluded from the per-category counts (it isn't a "column"), but still counts toward
     *  overall pass/fail — a failing row-level check should still flip this table's badge to FAIL. */
    private static String buildTableHeaderSummary(List<ColumnCheckResult> checks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ColumnCheckResult c : checks) {
            if ("<row>".equals(c.column())) continue;
            counts.merge(friendlyExpectationDetail(c.expectationLabel()), 1, Integer::sum);
        }
        boolean allPassed = checks.stream().allMatch(ColumnCheckResult::passed);
        String countsText = counts.entrySet().stream()
                .map(e -> "<b>" + e.getValue() + "</b> " + e.getKey())
                .collect(java.util.stream.Collectors.joining(", "));
        return "<span style='font-size:11px;font-weight:400;color:#64748B;margin-left:12px'>"
                + countsText + "</span>"
                // margin-left:auto (not a fixed 8px) — confirmed live via screenshot: .rs-sub's
                // justify-content:space-between spreads left-over row width evenly across all 4
                // flex children's gaps, so a row with shorter stats text (more left-over space)
                // showed a stray blank gap between this badge and the chevron that longer-stats
                // rows (e.g. FINVCHMAIN) didn't. A flex item's auto margin absorbs all left-over
                // space in front of it before justify-content acts on what's left, so this badge
                // (and the chevron right after it) now always lands flush right with only
                // .rs-sub's normal gap:6px between them, independent of stats-text length.
                + "<span style='margin-left:auto;display:inline-flex;align-items:center'>"
                + FoldableSectionRenderer.badge(allPassed ? Status.PASS : Status.FAIL) + "</span>";
    }

    /** Item E: partitions {@code visible} by table (preserving first-seen table order) and renders
     *  one nested foldable sub-section per table — {@code ActualResultsLogger}'s own per-table
     *  grouping style — instead of one flat table with every table's columns interleaved. */
    private static String buildGroupedFullDetailsHtml(List<ColumnCheckResult> visible) {
        Map<String, List<ColumnCheckResult>> byTable = visible.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ColumnCheckResult::table, LinkedHashMap::new, java.util.stream.Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ColumnCheckResult>> entry : byTable.entrySet()) {
            List<ColumnCheckResult> checks = entry.getValue();
            // Per explicit request: state per-category column counts (Mandatory/From UI/System-
            // generated/etc.) plus overall pass/fail right in the section's own <summary> line —
            // native <details> always renders <summary> regardless of open/closed state, so this
            // is visible even collapsed, same idea as the "Validation Summary" line above.
            String headerHtml = FoldableSectionRenderer.headerLabel(FoldableSectionRenderer.ICO_TABLE, entry.getKey(), 6)
                    + buildTableHeaderSummary(checks);
            sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "", headerHtml));

            // Item T: a table whose rows are ALL "EXPECTED INSERT" (Create) or ALL "EXPECTED
            // DELETE" (Delete) never has a meaningful Before or After (respectively) — confirmed
            // live: every single row showed Before=(absent) for a Create invocation, adding no
            // information and burying what actually matters (the real written value). Collapse to
            // Row | Column | Value | Result in that case; a mixed table (Edit/Revert, where some
            // columns are MUST_CHANGE/MUST_NOT_CHANGE and others might be an INSERT/DELETE within
            // the same table) keeps the full Before/After shape.
            //
            // Item X (Round 5): broadened from a strict allMatch("EXPECTED INSERT"/"EXPECTED
            // DELETE") — Item G's MANDATORY-not-null columns and Item Z's MUST_CHANGE-overridden
            // user-supplied columns both legitimately mix into an otherwise all-insert/all-delete
            // table (confirmed live: vchtype's "MANDATORY (not null)" row broke the old allMatch,
            // so Create's tables never actually collapsed). A column with one of these "resolved
            // override" labels has no independent Before/After direction of its own — it only
            // ever appears alongside a real insert or delete row for the same business key — so
            // it's compatible with whichever direction the row-level INSERT/DELETE checks in the
            // same table already establish. A table's rows are never a genuine mix of insert AND
            // delete within one scenario run, so hasInsert/hasDelete are mutually exclusive here.
            boolean hasInsert = checks.stream().anyMatch(c -> c.expectationLabel().startsWith("EXPECTED INSERT"));
            boolean hasDelete = checks.stream().anyMatch(c -> c.expectationLabel().startsWith("EXPECTED DELETE"));
            // Item D (Round 9): SYSTEM_CHANGE-labeled overrides (e.g. finpost/fincosttag's
            // systemChangeTo("ecode"/"admou_code", ...)) are just as before-less as
            // MANDATORY/MUST_CHANGE on an insert/delete row — omitting this prefix silently broke
            // the collapse for any table mixing in a systemChangeTo override, falling back to the
            // full Before/After shape for a Create-mode table that genuinely has no Before.
            boolean allCollapsible = checks.stream().allMatch(c -> c.expectationLabel().startsWith("EXPECTED INSERT")
                    || c.expectationLabel().startsWith("EXPECTED DELETE")
                    || c.expectationLabel().startsWith("MANDATORY")
                    || c.expectationLabel().startsWith("MUST_CHANGE")
                    || c.expectationLabel().startsWith("SYSTEM_CHANGE"));
            if (allCollapsible && (hasInsert ^ hasDelete)) {
                sb.append(FoldableSectionRenderer.tableHtml(
                        buildColumnRowsCollapsed(checks, hasInsert), WIDTHS_6COL_RESULT));
            } else {
                sb.append(FoldableSectionRenderer.tableHtml(buildColumnRows(checks), WIDTHS_9COL_RESULT));
            }
            sb.append(FoldableSectionRenderer.sectionClose());
        }
        return sb.toString();
    }

    /** Item T: the collapsed insert-only/delete-only shape — {@code Row (Business Key) | Column |
     *  Value | Detail | Classification | Result} — "Value" is {@code afterDisplay()} for an
     *  all-insert table (what was actually written), or {@code beforeDisplay()} for an all-delete
     *  table (what existed right before removal). "Table" is dropped — constant across every row
     *  in this group (the section header already names the table), so repeating it would add
     *  noise, not information. (The raw "Expectation" label WAS also dropped here originally, on
     *  the assumption every row in a collapsed group shared one expectation — no longer true once
     *  Item X started tolerating a mix of EXPECTED INSERT/DELETE, MANDATORY, and MUST_CHANGE
     *  labels in the same group; "Detail" below restores that information in friendlier words.)
     *
     *  <p>"Classification"/"Detail" (both added per explicit request) reuse the same
     *  {@link ChangeClassifier#classify}/{@link #friendlyExpectationDetail} derivations used
     *  elsewhere — not a second hand-authored mapping — so a reader can see USER_CHANGE/
     *  SYSTEM_CHANGE/CASCADE_CHANGE/etc. and WHY the check exists (Mandatory/From UI/System-
     *  generated/etc.) right on the row itself. "Row (Business Key)" and "Value" are also
     *  enriched with a human-readable name in parentheses for glcode/slcode/payment_mode —
     *  see {@link #enrichBusinessKeyDisplay}/{@link #enrichValueDisplay}. */
    private static String[][] buildColumnRowsCollapsed(List<ColumnCheckResult> checks, boolean isInsert) {
        String[][] table = new String[checks.size() + 1][6];
        table[0] = new String[]{"Row (Business Key)", "Column", "Value", "Detail", "Classification", "Result"};
        for (int i = 0; i < checks.size(); i++) {
            ColumnCheckResult c = checks.get(i);
            String rawValue = isInsert ? c.afterDisplay() : c.beforeDisplay();
            String value = enrichValueDisplay(c.column(), rawValue);
            String businessKey = enrichBusinessKeyDisplay(c.businessKey());
            String detail = friendlyExpectationDetail(c.expectationLabel());
            String classification = ChangeClassifier.classify(c, null).name();
            String result = FoldableSectionRenderer.badge(
                    c.knownIssueNote() != null ? Status.KNOWN_ISSUE : c.passed() ? Status.PASS : Status.FAIL);
            String[] row = {businessKey, c.column(), value, detail, classification, result};
            table[i + 1] = c.knownIssueNote() != null ? amberRow(row) : c.passed() ? row : redRow(row);
        }
        return table;
    }

    // ─── Human-readable code enrichment (added per explicit request) — resolves glcode/slcode/
    // ycode/admsite_code(_owner)/ref_admsite_code/admou_code/doccode/ecode family to their
    // master-data name, vchtype/release_status to their enum label, and payment_mode to its
    // label, live, so a reader isn't left staring at a bare numeric code or single-character DB
    // flag. Cached per-run (master data doesn't change mid-run); a lookup failure or unresolvable
    // code leaves the original text untouched rather than throwing. Column-name-only dispatch, so
    // one branch covers every table that happens to share that column name (finvchmain,
    // finvchdet, finpost, fincosttag, ...). ──

    private static final Map<Long, String> GL_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> SL_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> YEAR_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> SITE_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> OU_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> DOC_SCHEME_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, String> EMP_ABBRE_CACHE = new ConcurrentHashMap<>();
    private static final Pattern CODE_SEGMENT_PATTERN = Pattern.compile("(?i)(glcode|slcode)=(-?\\d+)");

    private static String resolveGlName(long glcode) {
        return GL_NAME_CACHE.computeIfAbsent(glcode, code -> {
            try {
                FinGl gl = DatabaseManager.getJdbi().onDemand(FinGlDao.class).findByGlCode(code);
                return gl != null ? gl.getGlname() : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static String resolveSlName(long slcode) {
        return SL_NAME_CACHE.computeIfAbsent(slcode, code -> {
            try {
                FinSl sl = DatabaseManager.getJdbi().onDemand(FinSlDao.class).findBySlcode(code);
                return sl != null ? sl.getSlname() : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Resolves {@code ycode} to its fiscal-year name PLUS date range, e.g. "2025-26 (01/04/2025
     *  to 31/03/2026)" — {@code yname} alone would be a thin label, so this mirrors the one
     *  existing place {@link AdmYear} is displayed, {@link AccountingPeriodResolver}'s own
     *  gathering log, reusing its boundary-exact {@code firstValidDay}/{@code lastValidDay}. */
    private static String resolveYearName(long ycode) {
        return YEAR_NAME_CACHE.computeIfAbsent(ycode, code -> {
            try {
                return DatabaseManager.getJdbi().onDemand(AdmYearDao.class).findByYcode(code)
                        .map(y -> y.getYname() + " ("
                                + AccountingPeriodResolver.firstValidDay(y).format(VoucherReportUtils.DATE_FMT)
                                + " to "
                                + AccountingPeriodResolver.lastValidDay(y).format(VoucherReportUtils.DATE_FMT)
                                + ")")
                        .orElse(null);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Resolves an {@code admsite}-family code (admsite_code_owner/admsite_code/
     *  ref_admsite_code) to its site name. */
    private static String resolveSiteName(long code) {
        return SITE_NAME_CACHE.computeIfAbsent(code, c -> {
            try {
                return AdmSiteQuery.findNameByCode(DatabaseManager.getJdbi(), c);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Resolves {@code admou_code} to its organizational-unit name. */
    private static String resolveOuName(long code) {
        return OU_NAME_CACHE.computeIfAbsent(code, c -> {
            try {
                return AdmOuQuery.findNameByCode(DatabaseManager.getJdbi(), c);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Resolves {@code doccode} to its document scheme name. */
    private static String resolveDocSchemeName(long doccode) {
        return DOC_SCHEME_NAME_CACHE.computeIfAbsent(doccode, code -> {
            try {
                return DatabaseManager.getJdbi().onDemand(AdmDocSchemeDao.class).findDocnameByDoccode(code);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Resolves an {@code ecode}-family code (ecode/release_ecode/last_access_ecode) to the
     *  employee's login {@code abbre} — not a full name, per explicit instruction, since no
     *  employee "display name" column/model exists in this codebase. */
    private static String resolveEmployeeAbbre(long ecode) {
        return EMP_ABBRE_CACHE.computeIfAbsent(ecode, code -> {
            try {
                return DatabaseManager.getJdbi().onDemand(HrEmpDao.class).findAbbreByEcode(code).orElse(null);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** Appends "(name)" after every {@code glcode=<n>}/{@code slcode=<n>} segment in a
     *  business-key display string, e.g. {@code "glcode=1497, slcode=—"} →
     *  {@code "glcode=1497 (Advance Income Tax), slcode=—"}. Segments the lookup can't resolve
     *  (non-numeric, no master-data match) are left exactly as they were. */
    private static String enrichBusinessKeyDisplay(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) return businessKey;
        Matcher m = CODE_SEGMENT_PATTERN.matcher(businessKey);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(0);
            try {
                long code = Long.parseLong(m.group(2));
                String name = "glcode".equalsIgnoreCase(m.group(1)) ? resolveGlName(code) : resolveSlName(code);
                if (name != null) replacement = m.group(0) + " (" + name + ")";
            } catch (NumberFormatException ignored) { /* leave segment untouched */ }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Same idea as {@link #enrichBusinessKeyDisplay}, but for a VALUE/Before/After cell whose own
     *  COLUMN is one of the coded/FK columns below — the cell holds the bare code with no "col="
     *  prefix, e.g. column {@code "glcode"}, value {@code "1497"} → {@code "1497 (Advance Income
     *  Tax)"}; column {@code "payment_mode"}, value {@code "H"} → {@code "H (Cash)"} (see
     *  {@link PaymentSelectionType#fromDbCode}). Covers: {@code glcode}/{@code slcode} (GL/SL
     *  name), {@code ycode} (fiscal-year name + date range), {@code admsite_code_owner}/
     *  {@code admsite_code}/{@code ref_admsite_code} (site name), {@code admou_code} (OU name),
     *  {@code doccode} (document scheme name), {@code ecode}/{@code release_ecode}/
     *  {@code last_access_ecode} (employee login abbre), {@code vchtype} ({@link VoucherType}
     *  label), {@code release_status} ({@link VoucherReleaseStatus} label), {@code payment_mode}
     *  ({@link PaymentSelectionType} label). Dispatch is by column name only, so one branch covers
     *  every table sharing that column (finvchmain, finvchdet, finpost, fincosttag, ...). Any
     *  other column, sentinel display ("—", "(absent)", "(present)"), or unresolvable code returns
     *  the value unchanged. */
    private static String enrichValueDisplay(String column, String value) {
        if (column == null || value == null || value.isBlank()
                || "—".equals(value) || value.startsWith("(")) {
            return value;
        }
        try {
            if ("glcode".equalsIgnoreCase(column)) {
                String name = resolveGlName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("slcode".equalsIgnoreCase(column)) {
                String name = resolveSlName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("ycode".equalsIgnoreCase(column)) {
                String name = resolveYearName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("admsite_code_owner".equalsIgnoreCase(column) || "admsite_code".equalsIgnoreCase(column)
                    || "ref_admsite_code".equalsIgnoreCase(column)) {
                String name = resolveSiteName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("admou_code".equalsIgnoreCase(column)) {
                String name = resolveOuName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("doccode".equalsIgnoreCase(column)) {
                String name = resolveDocSchemeName(Long.parseLong(value.trim()));
                return name != null ? value + " (" + name + ")" : value;
            }
            if ("ecode".equalsIgnoreCase(column) || "release_ecode".equalsIgnoreCase(column)
                    || "last_access_ecode".equalsIgnoreCase(column)) {
                String abbre = resolveEmployeeAbbre(Long.parseLong(value.trim()));
                return abbre != null ? value + " (" + abbre + ")" : value;
            }
        } catch (NumberFormatException ignored) { /* fall through unchanged */ }
        if ("payment_mode".equalsIgnoreCase(column)) {
            PaymentSelectionType type = PaymentSelectionType.fromDbCode(value.trim());
            if (type != null) return value + " (" + type.label() + ")";
        }
        if ("vchtype".equalsIgnoreCase(column)) {
            try {
                return value + " (" + VoucherType.fromCode(value.trim()).label() + ")";
            } catch (IllegalArgumentException ignored) { /* unknown code — leave unchanged */ }
        }
        if ("release_status".equalsIgnoreCase(column)) {
            VoucherReleaseStatus status = VoucherReleaseStatus.fromCode(value.trim());
            if (status != null) return value + " (" + status.label() + ")";
        }
        return value;
    }

    /** Friendly, business-readable rewording of a raw {@code expectationLabel()} — added per
     *  explicit request ("add the detail like Mandatory, FROM UI, etc."). Distinct from
     *  Classification (a coarser bucket shared with "Change Classification Summary"): this states
     *  WHY the check exists in plain words, not which bucket it rolls up into. */
    private static String friendlyExpectationDetail(String rawLabel) {
        if (rawLabel == null) return "—";
        // Round 6: echo the label's own parenthetical instead of one hardcoded string — MANDATORY
        // now covers 3 distinct sub-cases (not-null / must-be-blank / one-of, see ColumnRuleEngine's
        // strategy-aware label builder), and re-hardcoding each here would drift the moment a new
        // one is added. "MANDATORY (not null)" -> "Mandatory (not null)", etc.
        if (rawLabel.startsWith("MANDATORY")) return "Mandatory" + rawLabel.substring("MANDATORY".length());
        if (rawLabel.startsWith("MUST_CHANGE")) return "From UI (user-supplied)";
        if (rawLabel.startsWith("SYSTEM_CHANGE")) return "System-generated";
        if (rawLabel.startsWith("MUST_NOT_CHANGE")) return "Unchanged (expected)";
        if (rawLabel.startsWith("EXPECTED INSERT")) return "New row (Insert)";
        if (rawLabel.startsWith("EXPECTED DELETE")) return "Removed row (Delete)";
        if (rawLabel.startsWith("IGNORE")) return "Ignored (not compared)";
        if (rawLabel.startsWith("RECREATED")) return "Recreated (delete + reinsert)";
        return rawLabel;
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

    /** Builds the "Exceptions" table's rows — one per {@link ValidationFailure}, always styled red
     *  ({@link #redRow}) since every row here is, by definition, a failure. */
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

    /** Builds the "Change Classification Summary" table: tallies every column check's
     *  {@link ChangeClassifier#classify} result into passed/failed counts per
     *  {@link ChangeClassification}, including classifications with zero checks (so the table's
     *  shape is always the full enum, not just whichever buckets happened to fire). */
    private static String[][] buildClassificationSummaryTable(DiffResult result) {
        Map<ChangeClassification, int[]> counts = new LinkedHashMap<>(); // [passed, failed]
        for (ChangeClassification c : ChangeClassification.values()) counts.put(c, new int[2]);

        for (ColumnCheckResult check : result.columnChecks()) {
            if ("<row>".equals(check.column()) && !check.passed()) continue; // structural failures already in Exceptions
            ChangeClassification classification = ChangeClassifier.classify(check, null);
            int[] tally = counts.get(classification);
            tally[check.passed() ? 0 : 1]++;
        }

        // Item S: a 4th "What it means" column — a reader shouldn't need to already know what
        // CASCADE_CHANGE/AUDIT_CHANGE/etc. mean to understand this table.
        String[][] table = new String[ChangeClassification.values().length + 1][4];
        table[0] = new String[]{"Classification", "What it means", "Passed", "Failed"};
        int i = 1;
        for (Map.Entry<ChangeClassification, int[]> e : counts.entrySet()) {
            table[i++] = new String[]{e.getKey().name(), e.getKey().description(),
                    String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1])};
        }
        return table;
    }

    /** Builds the {@code Table | Row | Column | Expectation | Detail | Before | After |
     *  Classification | Result} rows for whatever {@code checks} it's given — the suppression
     *  filter (Item A, config-gated) is applied once by the caller ({@link #render}) BEFORE
     *  grouping/partitioning, not here, so a per-table slice (Item E's
     *  {@link #buildGroupedFullDetailsHtml}) never re-filters. "Classification"/"Detail" reuse the
     *  same derivations as {@link #buildColumnRowsCollapsed} — see that method's Javadoc; "Row"/
     *  "Before"/"After" get the same glcode/slcode/payment_mode name enrichment. */
    private static String[][] buildColumnRows(List<ColumnCheckResult> checks) {
        String[][] table = new String[checks.size() + 1][9];
        table[0] = new String[]{"Table", "Row (Business Key)", "Column", "Expectation", "Detail", "Before", "After", "Classification", "Result"};
        for (int i = 0; i < checks.size(); i++) {
            ColumnCheckResult c = checks.get(i);
            // A known-issue mismatch is technically `passed()` (it never fails the test) but must
            // NOT render indistinguishably from a clean PASS — a distinct badge keeps it visible
            // as the confirmed, already-tracked defect it is.
            String classification = ChangeClassifier.classify(c, null).name();
            String detail = friendlyExpectationDetail(c.expectationLabel());
            String businessKey = enrichBusinessKeyDisplay(c.businessKey());
            String before = enrichValueDisplay(c.column(), c.beforeDisplay());
            String after = enrichValueDisplay(c.column(), c.afterDisplay());
            String result = FoldableSectionRenderer.badge(
                    c.knownIssueNote() != null ? Status.KNOWN_ISSUE : c.passed() ? Status.PASS : Status.FAIL);
            String[] row = {
                    c.table(), businessKey, c.column(), c.expectationLabel(),
                    detail, before, after, classification, result
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

    /** Builds the "Audit Trail" table's rows — one per {@link AuditTableCheckResult}, i.e. one per
     *  audit-active {@code aud2_<table>} that participated for this document. */
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

    /** Which shape {@link #buildAuditFieldTable} should render — see {@link #resolveAuditFieldShape}. */
    private enum AuditFieldShape { ROW_LEVEL, INSERT_ONLY, DELETE_ONLY, FULL }

    /**
     * Decides whether "Old" and/or "New" columns carry any real information for this set of
     * checks — the same mode-aware principle Item T already applies to "Full Validation Details",
     * extended here to fix the identical recurring gap in this sibling table:
     * <ul>
     *   <li>{@link AuditFieldShape#ROW_LEVEL} — every check is a row-level INSERT/DELETE
     *       ({@code column() == null}, the usual Create/Delete shape) — Old AND New are always
     *       blank for every single row (there is no column to hold a value), so both are dropped
     *       entirely.</li>
     *   <li>{@link AuditFieldShape#INSERT_ONLY}/{@link AuditFieldShape#DELETE_ONLY} — every check
     *       is that one operation, but at least one carries real column data (e.g. an unasserted
     *       cascade column reported via {@code AuditComparator}'s "actual with no expected"
     *       branch) — only the direction that operation can ever populate is kept ("New" for
     *       INSERT, "Old" for DELETE).</li>
     *   <li>{@link AuditFieldShape#FULL} — a genuine mix (Edit/Revert, where UPDATE rows have both
     *       a real Old and New) — unchanged full shape.</li>
     * </ul>
     */
    private static AuditFieldShape resolveAuditFieldShape(List<AuditFieldCheckResult> checks) {
        if (checks.stream().allMatch(f -> f.column() == null)) return AuditFieldShape.ROW_LEVEL;
        if (checks.stream().allMatch(f -> f.operation() == com.gsl.validation.snapshot.audit.AuditOperation.INSERT)) {
            return AuditFieldShape.INSERT_ONLY;
        }
        if (checks.stream().allMatch(f -> f.operation() == com.gsl.validation.snapshot.audit.AuditOperation.DELETE)) {
            return AuditFieldShape.DELETE_ONLY;
        }
        return AuditFieldShape.FULL;
    }

    private static String[][] buildAuditFieldTable(List<AuditFieldCheckResult> fieldChecks, AuditFieldShape shape) {
        return switch (shape) {
            case ROW_LEVEL -> buildAuditFieldTableRowLevel(fieldChecks);
            case INSERT_ONLY -> buildAuditFieldTableCollapsed(fieldChecks, true);
            case DELETE_ONLY -> buildAuditFieldTableCollapsed(fieldChecks, false);
            case FULL -> buildAuditFieldTableFull(fieldChecks);
        };
    }

    /** {@code Table | Row (Business Key) | Operation | Status} — every row here is a row-level
     *  INSERT/DELETE with no column data at all; Column/Old/New would be "—" on every single row. */
    private static String[][] buildAuditFieldTableRowLevel(List<AuditFieldCheckResult> fieldChecks) {
        String[][] table = new String[fieldChecks.size() + 1][4];
        table[0] = new String[]{"Table", "Row (Business Key)", "Operation", "Status"};
        for (int i = 0; i < fieldChecks.size(); i++) {
            AuditFieldCheckResult f = fieldChecks.get(i);
            String[] row = {
                    f.table(), f.recordId(), f.operation() == null ? "—" : f.operation().name(),
                    FoldableSectionRenderer.badge(f.passed() ? Status.PASS : Status.FAIL)
            };
            table[i + 1] = f.passed() ? row : redRow(row);
        }
        return table;
    }

    /** {@code Table | Row (Business Key) | Column | Value | Status} — collapsed INSERT-only
     *  (isInsert=true, "Value" = expectedNew/actualNew) or DELETE-only (isInsert=false, "Value" =
     *  expectedOld/actualOld) shape. Prefers the ACTUAL side when present (a genuine reconstructed
     *  audit value) and falls back to the expected side (e.g. "(not asserted)") otherwise. */
    private static String[][] buildAuditFieldTableCollapsed(List<AuditFieldCheckResult> fieldChecks, boolean isInsert) {
        String[][] table = new String[fieldChecks.size() + 1][5];
        table[0] = new String[]{"Table", "Row (Business Key)", "Column", "Value", "Status"};
        for (int i = 0; i < fieldChecks.size(); i++) {
            AuditFieldCheckResult f = fieldChecks.get(i);
            String actual = isInsert ? f.actualNew() : f.actualOld();
            String expected = isInsert ? f.expectedNew() : f.expectedOld();
            String value = (actual != null && !"—".equals(actual)) ? actual : expected;
            String[] row = {
                    f.table(), f.recordId(), f.column() == null ? "—" : f.column(),
                    value == null ? "—" : value,
                    FoldableSectionRenderer.badge(f.passed() ? Status.PASS : Status.FAIL)
            };
            table[i + 1] = f.passed() ? row : redRow(row);
        }
        return table;
    }

    /** The original, unconditional 9-column shape — kept for a genuine mix of operations
     *  (Edit/Revert), where UPDATE rows have both a real Old and New value. */
    private static String[][] buildAuditFieldTableFull(List<AuditFieldCheckResult> fieldChecks) {
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

}
