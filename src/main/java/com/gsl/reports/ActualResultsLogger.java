package com.gsl.reports;

import com.gsl.database.DatabaseManager;
import com.gsl.database.JdbiSqlLogger;
import com.gsl.reports.FoldableSectionRenderer.Status;
import com.gsl.reports.contract.TestResultsAccumulator;
import com.gsl.reports.contract.TestResultsRenderer;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.enums.Finance.Operations.APVoucher.OnAccountValues;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinCostTag;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.validation.ValidationSwitches;
import com.gsl.validation.api.ApiCheckRow;
import com.gsl.validation.snapshot.finance.AuditStateSummary;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationReporter;

import static com.gsl.reports.FoldableSectionRenderer.ICO_DB;
import static com.gsl.reports.FoldableSectionRenderer.ICO_DOC;
import static com.gsl.reports.FoldableSectionRenderer.ICO_FLAG;
import static com.gsl.reports.FoldableSectionRenderer.ICO_LOOKUP;
import static com.gsl.reports.FoldableSectionRenderer.ICO_MONITOR;
import static com.gsl.reports.FoldableSectionRenderer.ICO_SUBLEDGER;
import static com.gsl.reports.FoldableSectionRenderer.ICO_TABLE;
import static com.gsl.reports.FoldableSectionRenderer.badge;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the "Actual Results" table from {@link ActualResultsHolder} to the Extent Report.
 *
 * Called by TestListener in both onTestSuccess and onTestFailure — the table therefore
 * appears in every test regardless of outcome. Null/unset fields render as "— (not reached)".
 */
public final class ActualResultsLogger {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ─── Summary-first / exception-first reporting (Database_Snapshot_Validation_Representation.md) ──
    // A per-render tally threaded via ThreadLocal rather than a new parameter on every row()/flagRow()
    // call site — buildPositiveHtml's ~15 section builders all funnel through those two renderers
    // already, so this is a zero-signature-change way to collect "did anything fail" without
    // rewriting each section builder's method signature.
    private static final ThreadLocal<Tally> CURRENT_TALLY = new ThreadLocal<>();
    /** Swapped for the real Summary+Exceptions HTML once the tally is complete (see {@link #buildPositiveHtml}). */
    private static final String SUMMARY_PLACEHOLDER = " RS_SUMMARY_PLACEHOLDER ";
    /** Swapped for {@code "open"}/{@code ""} on the two top-level sections once the tally is
     *  complete — collapsed by default when nothing failed, forced open when something did. */
    private static final String SECTION_OPEN_PLACEHOLDER = "RS_SECTION_OPEN_PLACEHOLDER";

    private static final class Tally {
        int checked, passed, failed, skipped, known;
        final List<String[]> failures = new ArrayList<>(); // {field, expected, actual}
    }


    // ── Per-section report modes (ValidationSwitches) ───────────────────────────
    /** True while a FAILURES_ONLY section is being built: row helpers still tally every row but render only Fail / Known-issue ones. */
    private static final ThreadLocal<Boolean> FAILURES_ONLY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static void beginSection(ValidationSwitches.Mode mode) {
        FAILURES_ONLY.set(mode == ValidationSwitches.Mode.FAILURES_ONLY);
    }

    private static boolean suppressed(Status status) {
        return FAILURES_ONLY.get() && (status == Status.PASS || status == Status.SKIP);
    }

    /** Closes a section built since {@code start}: OFF drops it; FAILURES_ONLY drops sub-tables left empty, and the whole section when nothing failed. */
    private static void endSection(StringBuilder sb, int start, ValidationSwitches.Mode mode) {
        FAILURES_ONLY.set(false);
        if (mode == ValidationSwitches.Mode.FULL) return;
        if (mode == ValidationSwitches.Mode.OFF) { sb.setLength(start); return; }
        String html = sb.substring(start);
        if (!html.contains("Fail</span>") && !html.contains("Known Issue</span>")) { sb.setLength(start); return; }
        String pruned = EMPTY_SUBSECTION.matcher(html).replaceAll("");
        sb.setLength(start);
        sb.append(pruned);
    }

    /** A sub-section whose table body is empty (nothing failed in it) and which contains no nested section. */
    private static final java.util.regex.Pattern EMPTY_SUBSECTION = java.util.regex.Pattern.compile(
            "<details[^>]*>(?:(?!<details).)*?<tbody>\\s*</tbody>(?:(?!<details).)*?</details>", java.util.regex.Pattern.DOTALL);

    /** "API Validation" top section: Check | Expected | Actual | Status, one sub-table per group (Document, Line N, ...). */
    private static String buildApiValidationSection(ActualResultsHolder h, ValidationSwitches.Mode mode) {
        List<ApiCheckRow> rows = h.getApiValidationRows();
        if (mode == ValidationSwitches.Mode.OFF || rows == null || rows.isEmpty()) return "";
        boolean failuresOnly = mode == ValidationSwitches.Mode.FAILURES_ONLY;
        Map<String, List<ApiCheckRow>> groups = new LinkedHashMap<>();
        for (ApiCheckRow r : rows) groups.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
        StringBuilder body = new StringBuilder();
        boolean anyShown = false;
        for (Map.Entry<String, List<ApiCheckRow>> g : groups.entrySet()) {
            StringBuilder rowsHtml = new StringBuilder();
            for (ApiCheckRow r : g.getValue()) {
                Status st = r.passed() ? Status.PASS : Status.FAIL;
                if (failuresOnly && r.passed()) { tally(st, r.check(), r.expected(), r.actual()); continue; }
                rowsHtml.append(row(r.check(), r.expected(), r.actual(), st));
            }
            if (rowsHtml.length() == 0) continue;
            anyShown = true;
            body.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                    FoldableSectionRenderer.headerLabel(ICO_TABLE, g.getKey(), 6)));
            body.append(tableOpen()).append(headerRow("Check", "Expected", "Actual", "Status"));
            body.append(rowsHtml).append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
        }
        if (!anyShown) return "";
        long failed = rows.stream().filter(r -> !r.passed()).count();
        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen(SECTION_OPEN_PLACEHOLDER, "rs-top rs-api", "",
                FoldableSectionRenderer.headerLabel(FoldableSectionRenderer.ICO_API, "API Validation", 8)));
        sb.append("<div style='padding:6px 14px 2px;font-size:11px;color:#64748B'>")
          .append(rows.size()).append(" checks &middot; ").append(rows.size() - failed).append(" passed &middot; ")
          .append(failed).append(" failed")
          .append(h.getApiValidationSource() == null ? "" : " &middot; " + esc(h.getApiValidationSource()))
          .append(failuresOnly ? " &middot; showing failures only" : "").append("</div>");
        sb.append(body).append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    /** No-op when no render is in progress (e.g. the negative-scenario path, which doesn't call
     *  {@link #buildPositiveHtml}) — {@code row()}/{@code flagRow()} can call this unconditionally. */
    private static void tally(Status status, String field, String expected, String actual) {
        Tally t = CURRENT_TALLY.get();
        if (t == null) return;
        t.checked++;
        switch (status) {
            case PASS -> t.passed++;
            case SKIP -> t.skipped++;
            case KNOWN_ISSUE -> t.known++;
            case FAIL -> { t.failed++; t.failures.add(new String[]{field, expected, actual}); }
        }
    }

    private ActualResultsLogger() {}

    /**
     * Returns a {@link TestResultsRenderer} singleton backed by {@link #flushToReport()}.
     * Used by {@link FinanceDomainReporter} to flush via the domain-neutral contract.
     * The {@code accumulator} argument is accepted but not used — {@link #flushToReport()}
     * fetches the current thread's {@link ActualResultsHolder} from its own ThreadLocal.
     */
    public static TestResultsRenderer renderer() {
        return acc -> flushToReport();
    }

    /**
     * Renders the current thread's {@link ActualResultsHolder} as an "Actual Results" step —
     * the negative-scenario table when {@code isNegativeScenario()}, else the full positive HTML
     * (UI Validation / DB Validation / DB Lookups). Call once per test, from both
     * {@code onTestSuccess} and {@code onTestFailure} (see {@link FinanceDomainReporter}), so the
     * table always appears regardless of outcome. No-op rendering (a single explanatory line)
     * when the holder was never populated at all — see {@link #isCompletelyUntouched}.
     */
    public static void flushToReport() {
        ActualResultsHolder h = ActualResultsHolder.get();
        // Item H: skip rendering entirely when the holder was never populated at all — e.g.
        // Revert's list-page flow validates exclusively via the separate Database Snapshot
        // Validation system (VoucherSnapshotValidator/GeneralVoucherRevertChangeSets) and never
        // touches this holder, so rendering the full "Actual Results" section here previously
        // produced nothing but a wall of "(not reached)"/Skip rows for fields that flow will never
        // reach. Self-heals once any field IS populated (e.g. a future Document-view Revert mode)
        // — this only fires when EVERY field is still at its untouched default.
        if (isCompletelyUntouched(h)) {
            StepExecutor.step("Actual Results", () -> ExtentLogger.info("Actual Results — not "
                    + "applicable (this test validates via Database Snapshot Validation instead)."));
            return;
        }
        JdbiSqlLogger.startCapturing();
        try {
            StepExecutor.step("Actual Results", () -> {
                if (h.isNegativeScenario()) {
                    renderNegativeTable(h);
                } else {
                    renderPositiveHtml(h);
                }
            });
        } finally {
            JdbiSqlLogger.stopCapturing();
        }
    }

    /** See {@link #flushToReport()}'s Item H guard — {@code true} only when every field the
     *  positive/negative renderers actually read is still at its untouched constructor default. */
    private static boolean isCompletelyUntouched(ActualResultsHolder h) {
        return h.getHeader() == null
                && h.getUiOwnerSite() == null
                && h.getUiCashBank() == null
                && h.getVoucherNo() == null
                && h.getDetRowCount() == -1
                && h.getPostEntryCount() == -1
                && (h.getApiValidationRows() == null || h.getApiValidationRows().isEmpty())
                // A UI-contract-only flow (e.g. GeneralVoucherUiValidation's tests) never touches
                // the DB/header/business fields above but DOES populate this — without this check,
                // such a flow was incorrectly classified as "completely untouched" and its real
                // Actual Results data silently discarded in favor of the generic placeholder.
                && (h.getUiValidationResults() == null || h.getUiValidationResults().isEmpty());
    }

    // ─── Positive HTML ────────────────────────────────────────────────────────

    /** Builds and logs the positive-scenario HTML, degrading to a plain error message (logged,
     *  not thrown) if building the HTML itself fails — a broken report section must never fail
     *  the test it's reporting on. */
    private static void renderPositiveHtml(ActualResultsHolder h) {
        try {
            ExtentLogger.infoHtml(buildPositiveHtml(h));
        } catch (Exception e) {
            Log.warn("[ActualResultsLogger] Failed to build positive table: {}", e.getMessage());
            ExtentLogger.info("(Actual Results table could not be rendered: " + e.getMessage() + ")");
        }
    }

    /** Installs a fresh {@link Tally} for the duration of one render so {@link #row}/{@link #flagRow}
     *  calls made by {@link #buildPositiveHtmlBody} (and everything it calls) can accumulate
     *  pass/fail/skip counts and the failure list the Summary/Exceptions sections need — always
     *  removed afterwards so a stale tally never leaks into the next render on this thread. */
    private static String buildPositiveHtml(ActualResultsHolder h) {
        CURRENT_TALLY.set(new Tally());
        try {
            return buildPositiveHtmlBody(h);
        } finally {
            CURRENT_TALLY.remove();
        }
    }

    /**
     * Assembles the full "Actual Results" HTML body: the Validation Summary/Exceptions cards
     * (filled in afterwards via {@link #SUMMARY_PLACEHOLDER}/{@link #SECTION_OPEN_PLACEHOLDER}
     * once the tally is complete), a "UI Validation" foldable section (Document Information,
     * AP-only Flags, Sub-Ledger/Site-Allocation/Payment-Information sub-sections), a "DB
     * Validation" foldable section (finvchmain/finnar/finvchdet/finpost/fintag-or-fincosttag/
     * finchqdet-finchqmain, each its own sub-section), and — when any SQL was captured during
     * rendering — a "DB Lookups" section. Every {@code row()}/{@code flagRow()} call along the way
     * feeds the thread-local {@link Tally} that the placeholders are swapped for at the end.
     */
    private static String buildPositiveHtmlBody(ActualResultsHolder h) {
        FinVchMain header = h.getHeader();

        String siteActual = h.getUiOwnerSite() != null ? h.getUiOwnerSite() : NOT_REACHED;
        String glActual   = h.getUiCashBank()  != null ? h.getUiCashBank()  : NOT_REACHED;

        String[] exp = extractExpected(h);
        String expOwnerSite      = exp[0];
        String expCashBank       = exp[1];
        String expDocDate        = exp[2];
        String expReference      = exp[3];
        String expRemarks        = exp[4];
        String expSlCount        = exp[5];
        String expSelectDocEntry = exp[6];

        String docNoActual   = ns(h.getVoucherNo());
        String docDateActual = header != null && header.getVchdt() != null
                ? header.getVchdt().format(DATE_FMT) : NOT_REACHED;
        String refActual     = header != null ? ns(header.getRefno())   : NOT_REACHED;
        String remarkActual  = header != null ? ns(header.getNartext()) : NOT_REACHED;
        String vchcodeActual = header != null
                ? "1 record found (vchcode=" + header.getVchcode() + ")" : NOT_REACHED;
        String detActual     = h.getDetRowCount() >= 0
                ? h.getDetRowCount() + " row(s) found" : NOT_REACHED;
        String postActual    = h.getPostEntryCount() >= 0
                ? h.getPostEntryCount() + " entries, balanced" : NOT_REACHED;

        boolean isAp = "AP".equals(h.getVoucherType());
        boolean isGv = "GV".equals(h.getVoucherType());

        String tagActual;
        if      (h.getTagCount() == -2) tagActual = NOT_REACHED;
        else if (h.getTagCount() == -1) tagActual = isGv
                ? "skipped (no site-applicable rows)"
                : "skipped (ADVANCE_PAYMENT — no SelectDocument)";
        else                            tagActual = h.getTagCount() + " entries";

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.CSS);
        sb.append("<div class='rs-wrap'>");
        sb.append(FoldableSectionRenderer.STATUS_LEGEND);
        sb.append(SUMMARY_PLACEHOLDER);

        ValidationSwitches.Mode uiMode  = ValidationSwitches.mode(ValidationSwitches.Area.UI);
        ValidationSwitches.Mode apiMode = ValidationSwitches.mode(ValidationSwitches.Area.API);
        ValidationSwitches.Mode dbMode  = ValidationSwitches.mode(ValidationSwitches.Area.DB);
        int uiStart = sb.length();
        beginSection(uiMode);
        if (uiMode != ValidationSwitches.Mode.OFF) {
        // ── UI Validation section ──────────────────────────────────────────────
        sb.append(FoldableSectionRenderer.sectionOpen(SECTION_OPEN_PLACEHOLDER, "rs-top rs-ui", "",
                FoldableSectionRenderer.headerLabel(ICO_MONITOR, "UI Validation", 8)));

        // Document Information sub-section
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_DOC, "Document Information", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Field", "Expected (test input)", "Actual (UI)", "Status"));

        // Doc No — label depends on whether it's auto-assigned
        String docNoLabel = "Doc no";
        String docNoExpected = "(assigned on save)";
        // Item H bonus fix: derive from the raw field, not the already-normalized display string —
        // ns(null) renders a bare "—" (not the NOT_REACHED sentinel "— (not reached)"), so the old
        // docNoActual.equals(NOT_REACHED) check silently resolved to PASS-by-construction whenever
        // voucherNo was never set at all, instead of correctly reporting SKIP.
        Status docNoStatus = h.getVoucherNo() == null ? Status.SKIP : Status.PASS;
        sb.append(row(docNoLabel, docNoExpected, docNoActual, docNoStatus));

        sb.append(row("Owner site",     expOwnerSite, siteActual,   matchContains(siteActual, expOwnerSite)));
        appendUiContractRows(sb, h, "OWNER_SITE");
        sb.append(row("Cash / bank GL", expCashBank,  glActual,     matchContains(glActual, expCashBank)));
        appendUiContractRows(sb, h, "CASH_BANK");
        sb.append(row("Doc date",       expDocDate,   docDateActual, matchExact(docDateActual, expDocDate)));
        appendUiContractRows(sb, h, "DATE");
        sb.append(row("Reference",      expReference, refActual,     matchExact(refActual, expReference)));
        sb.append(row("Remarks",        expRemarks,   remarkActual,  matchExact(remarkActual, expRemarks)));
        appendUiContractRows(sb, h, "REMARKS");
        // GV-only fields with no business-match row of their own (Balance/Net Amount) and the
        // section's own header check — appended at the end of this same table instead of a new
        // section, per explicit user request.
        // (CASH_BANK_BALANCE is already merged by the "CASH_BANK" prefix above.)
        appendUiContractRows(sb, h, "GV_NET_AMOUNT", "SECTION_HEADER_DOCUMENT_INFORMATION", "SECTION_DOCUMENT_INFORMATION");
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());

        // Flags sub-section — AP only
        if (isAp) {
            String expOnAccount = h.getOnAccount() != null ? OnAccountValues.from(h.getOnAccount()).getLabel() : "—";
            String uiOnAccount  = h.getUiOnAccount() != null ? h.getUiOnAccount().trim() : NOT_REACHED;
            sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                    FoldableSectionRenderer.headerLabel(ICO_FLAG, "Flags", 6)));
            sb.append(tableOpen());
            sb.append(headerRow("Field", "SL Name", "SL ID", "Expected", "Actual (UI)", "Status"));
            // Document-level flag — no SL row association
            sb.append(flagRow("On account", "—", "—", expOnAccount, uiOnAccount, matchExact(uiOnAccount, expOnAccount)));
            sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
        }

        // Sub Ledger Information sub-section
        sb.append(buildSubLedgerSection(h));

        // Site Allocation Information sub-section — GV only, no AP/AR precedent
        // (Type2/Type3 rows' Allocate Sites tagging; unrelated to AP/AR's sub-ledger concept).
        if (isGv) {
            sb.append(buildSiteAllocationSection(h));
            // Round 8 Part B: was entirely absent for any payment mode.
            sb.append(buildPaymentInformationSection(h));
            // Round 23: page-styling (font-size/font-family) regression checks for the Edit form.
            sb.append(buildPageStylingSection(h));
        }
        sb.append(buildScopedUiContractSection(h));

        sb.append(FoldableSectionRenderer.sectionClose()); // end UI Validation
        }
        endSection(sb, uiStart, uiMode);

        // ── API Validation section (API read-back checks, db.available=no) ──
        sb.append(buildApiValidationSection(h, apiMode));


        int dbStart = sb.length();
        beginSection(dbMode);
        if (dbMode != ValidationSwitches.Mode.OFF) {
        // ── DB Validation section ──────────────────────────────────────────────
        sb.append(FoldableSectionRenderer.sectionOpen(SECTION_OPEN_PLACEHOLDER, "rs-top rs-db", "",
                FoldableSectionRenderer.headerLabel(ICO_DB, "DB Validation", 8)));

        // Item AH (Round 5): compact one-line audit-trail summary — deliberately NOT a full
        // table (would visually duplicate the Database Snapshot Validation section's own, more
        // detailed Audit Trail table). Only rendered when the caller actually deposited an
        // AuditStateSummary (Create's "Audit State — Before Save" step does; Edit/Revert/Delete
        // don't populate this holder at all — see Item H).
        if (h.getAuditState() != null) {
            sb.append(buildAuditStateSummaryLine(h.getAuditState()));
        }

        // finvchmain
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_TABLE, "finvchmain", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status vchmainStatus = vchcodeActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("Record existence", "1 record created", vchcodeActual, vchmainStatus));
        String dbSiteActual = resolveOwnerSite(header);
        String dbGlActual   = resolveCashBankGl(header);
        sb.append(row("Owner site",   expOwnerSite, dbSiteActual, matchContains(dbSiteActual, expOwnerSite)));
        sb.append(row("Cash/bank GL", expCashBank,  dbGlActual,   matchContains(dbGlActual, expCashBank)));
        sb.append(row("vchdt",        expDocDate,   docDateActual, matchExact(docDateActual, expDocDate)));
        sb.append(row("refno",        expReference, refActual,     matchExact(refActual, expReference)));
        sb.append(row("nartext",      expRemarks,   remarkActual,  matchExact(remarkActual, expRemarks)));
        String entrySourceActual = header != null && header.getEntrySource() != null
                ? header.getEntrySource() : NOT_REACHED;
        String creationDtActual;
        Status creationDtStatus;
        if (header == null || header.getCreationDt() == null) {
            creationDtActual = NOT_REACHED;
            creationDtStatus = Status.SKIP;
        } else if (h.getCreationDtDelaySeconds() != null) {
            long delay = h.getCreationDtDelaySeconds();
            long tol   = h.getCreationDtToleranceSeconds();
            creationDtActual = delay + "s delay — within " + tol + "s tolerance";
            creationDtStatus = delay <= tol ? Status.PASS : Status.FAIL;
        } else {
            creationDtActual = header.getCreationDt().format(DATE_FMT);
            creationDtStatus = Status.PASS;
        }
        sb.append(row("entry_source", "WEB", entrySourceActual,
                entrySourceActual.equals(NOT_REACHED) ? Status.SKIP
                : entrySourceActual.matches("(?i)WEB|API") ? Status.PASS : Status.FAIL));
        java.time.LocalDateTime saveTime = h.getSaveStartTime();
        String creationDtExpected = saveTime != null
                ? "saved at " + saveTime.format(TIME_FMT) + " (within " + h.getCreationDtToleranceSeconds() + "s)"
                : "not null";
        sb.append(row("creation_dt", creationDtExpected, creationDtActual, creationDtStatus));
        // Items H/I (Round 9): doccode/admou_code were asserted only in the DiffEngine/changeset
        // layer, invisible here — no independently-known "expected" value at this layer, so shown
        // as a not-null presence check (the DiffEngine layer still owns the exact-value assertion).
        String doccodeActual = header != null && header.getDoccode() != null
                ? ns(header.getDoccode()) : NOT_REACHED;
        sb.append(row("doccode", "not null", doccodeActual,
                doccodeActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS));
        String admouCodeActual = header != null && header.getAdmouCode() != null
                ? ns(header.getAdmouCode()) : NOT_REACHED;
        sb.append(row("admou_code", "not null", admouCodeActual,
                admouCodeActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS));
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());

        // finnar — Round 19: promoted out of finvchmain's own table into its own dedicated
        // sub-section (was a single row folded inside finvchmain, unlike every other table here,
        // which each get their own section — inconsistent, and easy to overlook). Item F (Round 9)
        // originally added this; mirrors "Database Validation"'s own finnar table display.
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_TABLE, "finnar", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        com.gsl.model.finance.FinNar finnar = h.getFinnar();
        String finnarActual = header == null ? NOT_REACHED
                : (finnar != null ? "narcode=" + finnar.getNarcode() + " (" + ns(finnar.getNartext()) + ")"
                        : "not registered");
        sb.append(row("Registered", "registered", finnarActual,
                header == null ? Status.SKIP : (finnar != null ? Status.PASS : Status.FAIL)));
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());

        // finvchdet
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_TABLE, "finvchdet", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status detStatus = detActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("Row count", expSlCount + " row(s)", detActual, detStatus));
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());

        // finpost
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_TABLE, "finpost", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status postStatus = postActual.equals(NOT_REACHED) ? Status.SKIP : Status.PASS;
        sb.append(row("GL balance", "GL balanced", postActual, postStatus));
        sb.append("</tbody>").append(TABLE_CLOSE);
        if (h.getPostEntries() != null && !h.getPostEntries().isEmpty()) {
            sb.append(buildGlEntriesTable(h.getPostEntries()));
        }
        sb.append(FoldableSectionRenderer.sectionClose());

        // fintag (AP/AR: adjustment tagging) / fincosttag (GV: site allocation tagging) —
        // a structurally different table/concept per voucher type, not a renamed one; see
        // GeneralVoucherDbValidator/FinCostTagValidator vs FinTagValidator/FinTagApValidator.
        String tagSectionLabel = isGv ? "fincosttag (Site Allocation)" : "fintag";
        String tagRowLabel     = isGv ? "Site allocation tag check" : "Tag check";
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_TABLE, tagSectionLabel, 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Column", "Expected", "Actual", "Status"));
        Status tagStatus;
        if (h.getTagCount() == -2) tagStatus = Status.SKIP;
        else if (h.getTagCount() == -1) tagStatus = Status.SKIP;
        else tagStatus = Status.PASS;
        sb.append(row(tagRowLabel, expSelectDocEntry, tagActual, tagStatus));
        sb.append("</tbody>").append(TABLE_CLOSE);
        // Item I: real per-row site/amount/GL detail, mirroring finpost's own GL-entries table
        // above — previously computed in full by GeneralVoucherDbValidator then discarded, leaving
        // only the row count visible here.
        if (h.getCostTagEntries() != null && !h.getCostTagEntries().isEmpty()) {
            sb.append(buildCostTagEntriesTable(h.getCostTagEntries()));
        }
        sb.append(FoldableSectionRenderer.sectionClose());

        // finchqdet/finchqmain — Round 19: cheque-book detail (Level 5 for GV / the equivalent
        // check for AP, Round 20), previously never surfaced in "Actual Results" at all (only the
        // validator's own pass/fail assertions existed in "Database Validation"). Rendered only
        // when ChequeBookValidator.assertKnownChequeIssued actually resolved a row — every
        // non-Cheque payment mode, and every synthetic/unknown cheque number (no finchqdet row to
        // resolve), correctly show nothing here rather than a wall of "not applicable" rows.
        // Round 20: gate loosened from GV-only to GV‖AP now that AP populates the same fields.
        if ((isGv || isAp) && h.getChequeDet() != null) {
            com.gsl.model.finance.FinChqDet chqDet = h.getChequeDet();
            sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                    FoldableSectionRenderer.headerLabel(ICO_TABLE, "finchqdet", 6)));
            sb.append(tableOpen());
            sb.append(headerRow("Column", "Expected", "Actual", "Status"));
            sb.append(row("stat", "I (Issued)", ns(chqDet.getStat()),
                    "I".equalsIgnoreCase(chqDet.getStat()) ? Status.PASS : Status.FAIL));
            sb.append(row("finchqdet.bookcode", "not null", ns(chqDet.getBookcode()),
                    chqDet.getBookcode() != null ? Status.PASS : Status.FAIL));
            // Round 22: the actual cross-table comparison — does the voucher's own finvchdet.bookcode
            // really match the resolved finchqdet.bookcode? Previously only finchqdet's own
            // not-null-ness was shown here; the voucher's own value never surfaced anywhere.
            sb.append(row("finvchdet.bookcode", ns(chqDet.getBookcode()), ns(h.getChequeDetBookcodeOnVoucher()),
                    java.util.Objects.equals(chqDet.getBookcode(), h.getChequeDetBookcodeOnVoucher())
                            ? Status.PASS : Status.FAIL));
            sb.append(row("chqno", "—", ns(chqDet.getChqno()), Status.PASS));
            sb.append(row("chqdt", "—", ns(chqDet.getChqdt()), Status.PASS));
            sb.append(row("vchcode", "—", ns(chqDet.getVchcode()), Status.PASS));
            sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());

            if (h.getChequeMain() != null) {
                com.gsl.model.finance.FinChqMain chqMain = h.getChequeMain();
                sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                        FoldableSectionRenderer.headerLabel(ICO_TABLE, "finchqmain", 6)));
                sb.append(tableOpen());
                sb.append(headerRow("Column", "Expected", "Actual", "Status"));
                sb.append(row("Record existence", "1 record found", "bookcode=" + chqMain.getBookcode(), Status.PASS));
                sb.append(row("bookno", "—", ns(chqMain.getBookno()), Status.PASS));
                sb.append(row("glcode", "—", ns(chqMain.getGlcode()), Status.PASS));
                sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
            }
        }

        sb.append(FoldableSectionRenderer.sectionClose()); // end DB Validation

        // DB Lookups section — captured queries from this build
        List<String> captured = JdbiSqlLogger.getCaptured();
        if (!captured.isEmpty()) {
            sb.append(buildDbLookupsSection(captured));
        }

        }
        endSection(sb, dbStart, dbMode);

        sb.append("</div>"); // end rs-wrap
        sb.append(HIDE_TS_SCRIPT);

        Tally tally = CURRENT_TALLY.get();
        // Snapshot the failures list before rendering — buildExceptionsHtml's rows re-enter
        // row()/tally() (the same helper every other section uses), which would otherwise mutate
        // tally.failures while this very call is iterating over it (ConcurrentModificationException).
        String summaryAndExceptions = tally == null ? ""
                : buildSummaryHtml(tally) + buildExceptionsHtml(new ArrayList<>(tally.failures));
        String sectionOpenAttr = (tally != null && !tally.failures.isEmpty()) ? "open" : "";

        return sb.toString()
                .replace(SUMMARY_PLACEHOLDER, summaryAndExceptions)
                .replace(SECTION_OPEN_PLACEHOLDER, sectionOpenAttr);
    }

    /** Validation Summary card — prepended ahead of the UI/DB Validation sections (doc "Summary →
     *  Exceptions → Changed Rows → Column Details → Full Snapshot" hierarchy). */
    private static String buildSummaryHtml(Tally tally) {
        boolean overallPass = tally.failed == 0;
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:10px 14px;margin-bottom:8px;border-radius:8px;")
          .append("border:0.5px solid #E2E8F0;background:#F8FAFC'>");
        sb.append("<div style='font-size:11px;font-weight:600;color:#64748B;text-transform:uppercase;")
          .append("letter-spacing:.05em;margin-bottom:6px'>Validation Summary</div>");
        sb.append("<div style='display:flex;gap:18px;flex-wrap:wrap;align-items:center;font-size:12px'>");
        sb.append("<span>Fields Checked: <b>").append(tally.checked).append("</b></span>");
        sb.append("<span style='color:#27500A'>Passed: <b>").append(tally.passed).append("</b></span>");
        sb.append("<span style='color:#991B1B'>Failed: <b>").append(tally.failed).append("</b></span>");
        sb.append("<span style='color:#E65100'>Known issues: <b>").append(tally.known).append("</b></span>");
        sb.append("<span style='color:#5F5E5A'>Skipped: <b>").append(tally.skipped).append("</b></span>");
        sb.append("<span>Overall Result: ").append(badge(overallPass ? Status.PASS : Status.FAIL)).append("</span>");
        sb.append("</div></div>");
        return sb.toString();
    }

    /** Exceptions section — every failed field, surfaced right after the summary, before the full
     *  UI/DB Validation detail (doc's exception-first principle: "PASS information should be
     *  compressible"). Only called when {@code failures} is non-empty. */
    private static String buildExceptionsHtml(List<String[]> failures) {
        if (failures.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "background:#FEF2F2;color:#991B1B",
                "<span>❌ Exceptions (" + failures.size() + ")</span>"));
        sb.append(tableOpen());
        sb.append(headerRow("Field", "Expected", "Actual", "Status"));
        for (String[] f : failures) {
            sb.append(row(f[0], f[1], f[2], Status.FAIL));
        }
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    // ─── GL entries sub-table ─────────────────────────────────────────────────

    /** Renders finpost's per-GL Dr/Cr breakdown as its own small table — GL name resolved via
     *  {@link #resolveGlName}. */
    private static String buildGlEntriesTable(List<FinPost> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;width:100%;padding:6px 12px 10px;background:#fff'>");
        sb.append("<div style='font-size:11px;color:#64748B;margin-bottom:5px;letter-spacing:.03em'>GL entries</div>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:12px;border:0.5px solid #E2E8F0;border-radius:6px;overflow:hidden;table-layout:fixed'>");
        sb.append("<thead><tr>");
        sb.append(glTh("GL",        "left",  "65%"));
        sb.append(glTh("Dr amount", "right", "17.5%"));
        sb.append(glTh("Cr amount", "right", "17.5%"));
        sb.append("</tr></thead><tbody>");
        for (FinPost e : entries) {
            String glName  = resolveGlName(e.getGlcode());
            String glLabel = glName + " (" + e.getGlcode() + ")";
            String dr = fmtAmount(e.getDamount());
            String cr = fmtAmount(e.getCamount());
            sb.append("<tr>");
            sb.append("<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;color:#1E293B'>").append(esc(glLabel)).append("</td>");
            sb.append(amountTd(dr));
            sb.append(amountTd(cr));
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    /** Item AH (Round 5): one compact line, one clause per persistence-map table — "✓ active"
     *  when audit-active, "— not audited" otherwise (a table registered active but physically
     *  trigger-disabled on this environment gets its own clause, e.g. "✓ active (trigger
     *  disabled)"). Built directly from the two sets {@link AuditStateSummary} already resolved —
     *  no new DB calls. */
    private static String buildAuditStateSummaryLine(AuditStateSummary auditState) {
        String[] tables = {"finvchmain", "finvchdet", "finpost", "fincosttag"};
        List<String> clauses = new ArrayList<>();
        for (String table : tables) {
            boolean active = auditState.auditActiveTables().contains(table);
            boolean triggerDisabled = auditState.auditTriggerDisabledTables().contains(table);
            clauses.add(table + " " + (active
                    ? (triggerDisabled ? "&#10003; active (trigger disabled)" : "&#10003; active")
                    : "&#8212; not audited"));
        }
        return "<div style='padding:4px 14px 8px;font-size:11px;color:#64748B'>Audit: "
                + String.join(", ", clauses) + "</div>";
    }

    /** Item I: {@code fincosttag}'s per-row site/GL/amount detail — same table chrome as
     *  {@link #buildGlEntriesTable}, one row per allocated site instead of one row per GL entry. */
    private static String buildCostTagEntriesTable(List<FinCostTag> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;width:100%;padding:6px 12px 10px;background:#fff'>");
        sb.append("<div style='font-size:11px;color:#64748B;margin-bottom:5px;letter-spacing:.03em'>Site allocations</div>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:12px;border:0.5px solid #E2E8F0;border-radius:6px;overflow:hidden;table-layout:fixed'>");
        sb.append("<thead><tr>");
        sb.append(glTh("Site",       "left",  "45%"));
        sb.append(glTh("GL",         "left",  "35%"));
        sb.append(glTh("Dr amount",  "right", "10%"));
        sb.append(glTh("Cr amount",  "right", "10%"));
        sb.append("</tr></thead><tbody>");
        for (FinCostTag e : entries) {
            String siteLabel = resolveSiteName(e.getRefAdmsiteCode()) + " (" + e.getRefAdmsiteCode() + ")";
            String glLabel = resolveGlName(e.getGlcode()) + " (" + e.getGlcode() + ")";
            String dr = fmtAmount(e.getDamount());
            String cr = fmtAmount(e.getCamount());
            sb.append("<tr>");
            sb.append("<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;color:#1E293B'>").append(esc(siteLabel)).append("</td>");
            sb.append("<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;color:#1E293B'>").append(esc(glLabel)).append("</td>");
            sb.append(amountTd(dr));
            sb.append(amountTd(cr));
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    /** Looks up a site's display name by code for {@link #buildCostTagEntriesTable}; falls back
     *  to {@code "Site <code>"} when the lookup fails or finds nothing. */
    private static String resolveSiteName(long siteCode) {
        try {
            AdmSiteDao dao = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class);
            AdmSite site = dao.findByCode(siteCode);
            return site != null ? site.getName() : "Site " + siteCode;
        } catch (Exception e) {
            return "Site " + siteCode;
        }
    }

    private static String glTh(String label) { return glTh(label, "left", null); }
    private static String glTh(String label, String align) { return glTh(label, align, null); }
    private static String glTh(String label, String align, String width) {
        String wStyle = width != null ? "width:" + width + ";" : "";
        return "<th style='background:#F8FAFC;color:#64748B;font-size:11px;font-weight:500;padding:5px 10px;"
                + wStyle + "text-align:" + align + ";border-bottom:0.5px solid #E2E8F0'>" + esc(label) + "</th>";
    }
    private static String amountTd(String val) {
        boolean blank = "—".equals(val);
        String color = blank ? "#64748B" : "#1E293B";
        return "<td style='padding:5px 10px;border-bottom:0.5px solid #E2E8F0;text-align:right;"
                + "font-family:monospace;color:" + color + "'>" + val + "</td>";
    }
    // ─── DB Lookups section ───────────────────────────────────────────────────

    /** Renders every SQL query {@code JdbiSqlLogger} captured while this report section itself
     *  was being built, as its own foldable "DB Lookups" section — transparency about the
     *  reporting layer's own DB cost, not part of the test's actual validation. */
    private static String buildDbLookupsSection(List<String> queries) {
        String title = "DB Lookups &mdash; " + queries.size() + " quer" + (queries.size() == 1 ? "y" : "ies")
                + " fired during rendering";
        return FoldableSectionRenderer.sectionOpen("open", "rs-lookup-sum", "",
                    FoldableSectionRenderer.headerLabel(ICO_LOOKUP, title, 6))
             + FoldableSectionRenderer.sqlQueriesTableHtml(queries)
             + FoldableSectionRenderer.sectionClose();
    }

    /** Delegates to {@link VoucherReportUtils#fmtAmount} — local shorthand for this file's many
     *  call sites. */
    private static String fmtAmount(BigDecimal v) {
        return VoucherReportUtils.fmtAmount(v);
    }

    // ─── Sub Ledger Information sub-section ──────────────────────────────────

    /**
     * Renders the "Sub Ledger Information" sub-section — GV's structurally different
     * Expense/Income Detail rows are delegated to {@link #buildGvExpenseIncomeSection} up front
     * (no sub-ledger-class/adjustment-type/payment-mode concept, and AP/AR-only footer logic
     * would otherwise risk a {@code ClassCastException}); AP/AR render their own per-row table
     * (AP adds Deduct-TDS/TDS-Info columns) plus the shared Payment Info blank-warning footer.
     */
    private static String buildSubLedgerSection(ActualResultsHolder h) {
        Object td = h.getTestData();

        // GV's Expense/Income Detail rows have no sub-ledger-class/adjustment-type/payment-mode
        // concept (see GeneralVoucherIncomeExpenseRowView's javadoc) — rendered by its own
        // dedicated builder rather than shoehorned into the AP/AR loop below, which would
        // otherwise risk a ClassCastException in buildPaymentWarningFooter (that method assumes
        // AP or AR).
        if (td instanceof MergedFinanceGeneralVoucherData) {
            return buildGvExpenseIncomeSection((MergedFinanceGeneralVoucherData) td);
        }

        List<?> slList = null;
        boolean isAp = false;

        if (td instanceof MergedFinanceApVoucherData) {
            slList = ((MergedFinanceApVoucherData) td).getSubLedgerInformationList();
            isAp = true;
        } else if (td instanceof MergedFinanceArVoucherData) {
            slList = ((MergedFinanceArVoucherData) td).getSubLedgerInformationList();
        }

        if (slList == null || slList.isEmpty()) return "";

        // Heading: AP uses isOnAccount() to distinguish Advance Payment vs Document Adjustment
        String slTypeLabel;
        if (isAp) {
            slTypeLabel = ((MergedFinanceApVoucherData) td).isOnAccount()
                    ? "Advance Payment" : "Document Adjustment";
        } else {
            slTypeLabel = "Document Adjustment";
        }

        boolean needsPaymentInfoWarning = isAp
                ? ((MergedFinanceApVoucherData) td).isNeedsPaymentInfoWarning()
                : ((MergedFinanceArVoucherData) td).isNeedsPaymentInfoWarning();
        Map<String, Boolean> popupMap  = h.getUiPaymentInfoPopupMap();
        Map<String, String>  uiTdsInfoMap = h.getUiTdsInfoMap();
        Map<String, String>  deductMap    = h.getUiDeductTdsMap();

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_SUBLEDGER,
                        "Sub Ledger Information &#8212; " + slTypeLabel, 6)));
        sb.append("<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>");

        if (isAp) {
            sb.append(headerRow("#", "SL name", "SL ID", "Amount (Dr)", "Adj type",
                    "Deduct TDS (UI)", "Payment Info Popup", "TDS Info"));
        } else {
            sb.append(headerRow("#", "SL name", "SL ID", "Amount (Cr)", "Adj type", "Payment Info Popup"));
        }

        int idx = 1;
        for (Object row : slList) {
            if (isAp && row instanceof SubLedgerInformation) {
                SubLedgerInformation sl = (SubLedgerInformation) row;
                String adjType = sl.getAdjustmentType() != null ? sl.getAdjustmentType().displayLabel() : "—";
                String amount  = VoucherReportUtils.fmtAmount(((MergedFinanceApVoucherData) td).expectedSavedAmountDr(sl));
                String key     = (sl.getId() != null && !sl.getId().isBlank()) ? sl.getId() : sl.getSubLedger();
                String tdsInfo = uiTdsInfoMap.containsKey(key) ? ns(uiTdsInfoMap.get(key)) : "—";
                String uiTds   = deductMap.containsKey(key) ? ns(deductMap.get(key)) : NOT_REACHED;
                String[] popup = resolvePopupCell(needsPaymentInfoWarning, popupMap, key);
                sb.append(slRowAp(idx++, ns(sl.getSubLedger()), ns(sl.getId()), amount, adjType,
                        uiTds, popup[0], popup[1], popup[2], tdsInfo));
            } else if (row instanceof com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation) {
                com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation sl =
                        (com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation) row;
                String adjType = sl.getAdjustmentType() != null ? sl.getAdjustmentType().displayLabel() : "—";
                String amount  = VoucherReportUtils.fmtAmount(sl.getAmountCr());
                String key     = (sl.getId() != null && !sl.getId().isBlank()) ? sl.getId() : sl.getSubLedger();
                String[] popup = resolvePopupCell(needsPaymentInfoWarning, popupMap, key);
                sb.append(slRowAr(idx++, ns(sl.getSubLedger()), ns(sl.getId()), amount, adjType,
                        popup[0], popup[1], popup[2]));
            }
        }

        sb.append("</tbody></table></div>");

        // Footer flag row — Payment Info blank warning (document-level)
        sb.append(buildPaymentWarningFooter(td, isAp, h.getUiPaymentInfoWarningAppeared()));

        sb.append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    /**
     * Renders GV's Expense/Income Detail rows — GL, Sub Ledger (Type3 rows only), a single
     * Dr/Cr amount column (a GV row posts to either {@code Expense(Dr)} or {@code Income(Cr)},
     * never both — see {@code ExpenseIncomeDetail.isDebit()}), and Is Reversal. No adjustment
     * type, payment mode, or TDS columns — GV rows have none of those concepts, and no payment
     * warning footer (GV's payment-info-check is document-level, not per-row; see
     * {@code MergedFinanceGeneralVoucherData}'s Javadoc).
     */
    private static String buildGvExpenseIncomeSection(MergedFinanceGeneralVoucherData gv) {
        List<ExpenseIncomeDetail> lines = gv.getExpenseIncomeDetailList();
        if (lines == null || lines.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_SUBLEDGER, "Expense/Income Detail", 6)));
        sb.append("<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>");
        sb.append(headerRow("#", "GL", "Sub Ledger", "Amount (Dr/Cr)", "Is Reversal"));

        int idx = 1;
        for (ExpenseIncomeDetail row : lines) {
            String amount = row.isDebit()
                    ? (row.getExpenseDr() != null ? VoucherReportUtils.fmtAmount(row.getExpenseDr()) + " Dr" : "—")
                    : (row.getIncomeCr()  != null ? VoucherReportUtils.fmtAmount(row.getIncomeCr()) + " Cr"  : "—");
            sb.append(slRowGv(idx++, ns(row.getGeneralLedger()), ns(row.getSubLedger()),
                    amount, row.isReversalYes() ? "YES" : "NO"));
        }
        sb.append("</tbody></table></div>").append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    /** Renders one row of {@link #buildGvExpenseIncomeSection}'s table (# badge/GL/Sub Ledger/
     *  Amount/Is Reversal). */
    private static String slRowGv(int idx, String gl, String subLedger, String amount, String isReversal) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append(td("<span style='font-size:10px;color:#64748B;background:#F8FAFC;"
                + "border:0.5px solid #E2E8F0;padding:2px 5px;border-radius:99px'>" + idx + "</span>"));
        sb.append(td(esc(gl)));
        sb.append(td(esc(subLedger)));
        sb.append(td(esc(amount)));
        sb.append(td(esc(isReversal)));
        sb.append("</tr>");
        return sb.toString();
    }

    /**
     * New sub-section, no AP/AR precedent — renders GV's Site Allocation rows (Type2/Type3 GL
     * lines' "Allocate Sites" popup entries) grouped by the line they belong to.
     */
    private static String buildSiteAllocationSection(ActualResultsHolder h) {
        Object td = h.getTestData();
        if (!(td instanceof MergedFinanceGeneralVoucherData)) return "";
        MergedFinanceGeneralVoucherData gv = (MergedFinanceGeneralVoucherData) td;
        List<SiteAllocation> allocations = gv.getSiteAllocationList();
        if (allocations == null || allocations.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_SUBLEDGER, "Site Allocation Information", 6)));
        sb.append("<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>");
        sb.append(headerRow("Line#", "Site Name", "Amount"));

        for (SiteAllocation sa : allocations) {
            StringBuilder row = new StringBuilder("<tr>");
            row.append(td(esc(ns(sa.getLineNo()))));
            row.append(td(esc(ns(sa.getSiteName()))));
            row.append(td(esc(VoucherReportUtils.fmtAmount(sa.getAmount()))));
            row.append("</tr>");
            sb.append(row);
        }
        sb.append("</tbody></table></div>").append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    /**
     * Round 8 Part B — GV's Payment Information section: was entirely absent for ANY payment
     * mode (not a Cheque-specific gap). Shows the resolved payment mode; for Cheque, defers
     * instrument/cheque-book detail to Database Validation's Level 5 check instead of duplicating
     * the same data here in a second style (the "one canonical place per kind of data" principle
     * from the report-deduplication review).
     */
    private static String buildPaymentInformationSection(ActualResultsHolder h) {
        Object td = h.getTestData();
        if (!(td instanceof MergedFinanceGeneralVoucherData)) return "";
        MergedFinanceGeneralVoucherData gv = (MergedFinanceGeneralVoucherData) td;
        boolean multiRow = gv.getExpenseIncomeDetailList() != null && gv.getExpenseIncomeDetailList().size() > 1
                && gv.getFixtureOptions() != null && gv.getFixtureOptions().isMultiRowPayment();

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_SUBLEDGER, "Payment Information", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Field", "Expected", "Actual", "Status"));
        if (!multiRow) {
            var paymentInfo = gv.getCashBankPaymentInfo();
            // No payment info supplied: the "payment info missing" variant relies on the app's own
            // post-save "instrument number missing" warning, which only exists for a Cheque-mode
            // counter-leg — so Cheque (the app default) is the expected mode there, not Cash.
            PaymentSelectionType expectedType = paymentInfo != null ? paymentInfo.getPaymentMode()
                    : gv.isNeedsPaymentInfoWarning() ? PaymentSelectionType.CHEQUE : PaymentSelectionType.CASH;
            sb.append(paymentModeRow("Payment mode", expectedType, h.getPaymentModeActual()));
            if (expectedType == PaymentSelectionType.CHEQUE) {
                sb.append(row("Cheque / instrument detail", "see Database Validation — Level 5 (cheque-book)",
                        "see Level 5", Status.SKIP));
            }
        } else {
            // One payment-mode row per grid row; a row without payment info is expected to store the
            // Bank-header default (Cheque), same rule as the single-payment case above.
            boolean anyCheque = false;
            for (var line : gv.getExpenseIncomeDetailList()) {
                var info = gv.paymentForLine(line.getLineNo());
                PaymentSelectionType expectedType = info != null ? info.getPaymentMode() : PaymentSelectionType.CHEQUE;
                anyCheque |= expectedType == PaymentSelectionType.CHEQUE;
                sb.append(paymentModeRow("Row " + line.getLineNo() + " payment mode"
                                + (info == null ? " (no payment info)" : ""),
                        expectedType, h.getPaymentModeActualByLine().get(line.getLineNo())));
            }
            if (anyCheque) {
                sb.append(row("Cheque / instrument detail", "see Database Validation — Level 5 (cheque-book)",
                        "see Level 5", Status.SKIP));
            }
        }
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    private static String paymentModeRow(String field, PaymentSelectionType expectedType, String actualCode) {
        String expectedLabel = expectedType != null ? expectedType.label() : "—";
        PaymentSelectionType actualType = PaymentSelectionType.fromDbCode(actualCode);
        String actualLabel = actualCode == null ? NOT_REACHED
                : (actualType != null ? actualType.label() + " (" + actualCode + ")" : actualCode);
        Status status = actualCode == null ? Status.SKIP : (actualType == expectedType ? Status.PASS : Status.FAIL);
        return row(field, expectedLabel, actualLabel, status);
    }

    /**
     * Round 23 — page-styling regression checks: font-size/font-family for 3 elements on the GV
     * Edit form (breadcrumb, window title, "Voucher No." saved-document-no field). Values are
     * deposited by {@code GeneralVoucherAdd.verifyEditFormFullyLoaded} right after the page is
     * confirmed genuinely rendered — that method also soft-asserts these same expected values
     * (this section is purely descriptive, like every other "Actual Results" row; it does not by
     * itself fail the test — see that method for the actual assertion). Skipped entirely (like
     * {@link #buildSiteAllocationSection}) when none of the 3 fields were ever populated (Create/
     * Revert/Delete never reach this measurement).
     */
    private static String buildPageStylingSection(ActualResultsHolder h) {
        if (h.getUiBreadcrumbFont() == null && h.getUiWindowTitleFont() == null
                && h.getUiSavedDocumentNoFont() == null) {
            return "";
        }
        String breadcrumbActual = h.getUiBreadcrumbFont()      != null ? h.getUiBreadcrumbFont()      : NOT_REACHED;
        String titleActual      = h.getUiWindowTitleFont()     != null ? h.getUiWindowTitleFont()     : NOT_REACHED;
        String voucherNoActual  = h.getUiSavedDocumentNoFont() != null ? h.getUiSavedDocumentNoFont() : NOT_REACHED;

        // Expected values confirmed live via DevTools (Fin.css/Adm.css/charts.css agree identically).
        String expBreadcrumb = "13px|Segoe UI Light, Open Sans";
        String expTitle      = "25px|Segoe UI Light, Open Sans";
        String expVoucherNo  = "12px|Segoe UI, Open Sans"; // deliberately no "Light" — confirmed different

        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_MONITOR, "Page Styling", 6)));
        sb.append(tableOpen());
        sb.append(headerRow("Field", "Expected (size|family)", "Actual (UI)", "Status"));
        sb.append(row("Breadcrumb font", expBreadcrumb, breadcrumbActual, matchExact(breadcrumbActual, expBreadcrumb)));
        sb.append(row("Window title font", expTitle, titleActual, matchExact(titleActual, expTitle)));
        sb.append(row("Voucher No. field font", expVoucherNo, voucherNoActual, matchExact(voucherNoActual, expVoucherNo)));
        sb.append("</tbody>").append(TABLE_CLOSE).append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    /** Resolves expected/actual/statusClass for Payment Info Popup per SL row.
     *  Returns String[3]: {expLabel, actualLabel, statusCssClass} */
    private static String[] resolvePopupCell(boolean needsPaymentInfoWarning,
                                             Map<String, Boolean> popupMap, String key) {
        String expLabel = needsPaymentInfoWarning ? "YES" : "NO";
        String uiLabel;
        String statusClass;
        if (!popupMap.containsKey(key)) {
            uiLabel     = NOT_REACHED;
            statusClass = "skip";
        } else {
            uiLabel     = Boolean.TRUE.equals(popupMap.get(key)) ? "YES" : "NO";
            statusClass = uiLabel.equals(expLabel) ? "pass" : "fail";
        }
        return new String[]{expLabel, uiLabel, statusClass};
    }

    /** Builds the Payment Info blank warning footer flag row. */
    private static String buildPaymentWarningFooter(Object td, boolean isAp, Boolean uiWarned) {
        boolean expWarning = isAp
                ? ((MergedFinanceApVoucherData) td).isNeedsPaymentInfoWarning()
                : ((MergedFinanceArVoucherData) td).isNeedsPaymentInfoWarning();
        String expStr  = expWarning ? "YES" : "NO";
        String uiStr   = uiWarned == null ? NOT_REACHED : (uiWarned ? "YES" : "NO");
        Status st      = uiWarned == null ? Status.SKIP : (uiStr.equals(expStr) ? Status.PASS : Status.FAIL);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:0 0 8px'><table class='rs-tbl'><tbody><tr>");
        sb.append(td("<span style='font-size:11px;color:#475569;font-weight:500'>"
                + "Payment Info blank warning</span>"));
        sb.append(td(esc(expStr)));
        sb.append(td(esc(uiStr)));
        sb.append("<td>" + badge(st) + "</td>");
        sb.append("</tr></tbody></table></div>");
        return sb.toString();
    }

    /** Renders one row of AP's Sub Ledger Information table (# badge/SL name/SL Id/amount/Adj
     *  type/Deduct TDS/Payment Info Popup/TDS Info). */
    private static String slRowAp(int idx, String slName, String slId, String amount,
                                   String adjType, String uiDeductTds,
                                   String expPopup, String uiPopup, String popupStatusClass,
                                   String tdsInfo) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append(td("<span style='font-size:10px;color:#64748B;background:#F8FAFC;"
                + "border:0.5px solid #E2E8F0;padding:2px 5px;border-radius:99px'>" + idx + "</span>"));
        sb.append(td(esc(slName)));
        sb.append(td("<span style='font-family:monospace'>" + esc(slId) + "</span>"));
        sb.append(td(esc(amount)));
        sb.append(td(esc(adjType)));
        // Deduct TDS — informational only, no status badge
        sb.append(td("<span style='font-size:11px;color:#475569'>" + esc(uiDeductTds) + "</span>"));
        // Payment Info Popup — expected vs actual with status
        sb.append(popupCell(expPopup, uiPopup, popupStatusClass));
        // TDS Info — shows "—" until uiTdsInfoMap is populated via readTdsInfo()
        sb.append(td("<span style='font-size:11px;color:#64748B'>" + esc(tdsInfo) + "</span>"));
        sb.append("</tr>");
        return sb.toString();
    }

    /** Renders one row of AR's Sub Ledger Information table (# badge/SL name/SL Id/amount/Adj
     *  type/Payment Info Popup) — same shape as {@link #slRowAp} minus the AP-only TDS columns. */
    private static String slRowAr(int idx, String slName, String slId, String amount,
                                   String adjType,
                                   String expPopup, String uiPopup, String popupStatusClass) {
        StringBuilder sb = new StringBuilder("<tr>");
        sb.append(td("<span style='font-size:10px;color:#64748B;background:#F8FAFC;"
                + "border:0.5px solid #E2E8F0;padding:2px 5px;border-radius:99px'>" + idx + "</span>"));
        sb.append(td(esc(slName)));
        sb.append(td("<span style='font-family:monospace'>" + esc(slId) + "</span>"));
        sb.append(td(esc(amount)));
        sb.append(td(esc(adjType)));
        sb.append(popupCell(expPopup, uiPopup, popupStatusClass));
        sb.append("</tr>");
        return sb.toString();
    }

    /** Renders the Payment Info Popup cell: "Exp: X → UI: Y [badge]" */
    private static String popupCell(String exp, String ui, String statusClass) {
        Status s = "pass".equals(statusClass) ? Status.PASS
                 : "fail".equals(statusClass) ? Status.FAIL
                 : Status.SKIP;
        return "<td><span style='font-size:11px'>Exp: " + esc(exp)
                + " &rarr; UI: " + esc(ui) + "</span> " + badge(s) + "</td>";
    }

    // ─── Negative table ───────────────────────────────────────────────────────

    /** Builds and logs the negative-scenario table via {@link ExtentLogger#infoTable}; a build
     *  failure is logged, not thrown — same fail-soft contract as {@link #renderPositiveHtml}. */
    private static void renderNegativeTable(ActualResultsHolder h) {
        try {
            boolean ui = ValidationSwitches.run(ValidationSwitches.Area.UI);
            boolean db = ValidationSwitches.run(ValidationSwitches.Area.DB);
            ExtentLogger.infoTable(java.util.Arrays.stream(buildNegativeTable(h))
                    .filter(r -> (ui || !r[0].startsWith("UI —")) && (db || !r[0].startsWith("DB —")))
                    .toArray(String[][]::new));
        } catch (Exception e) {
            Log.warn("[ActualResultsLogger] Failed to build negative table: {}", e.getMessage());
        }
    }

    /** Builds the fixed Field/Expected/Actual rows for a negative scenario — every DB table is
     *  expected untouched (save blocked by UI validation), so this is a static shape rather than
     *  a real query result, with the expected UI message pulled from whichever voucher DTO is
     *  present in {@code h.getTestData()}. */
    private static String[][] buildNegativeTable(ActualResultsHolder h) {
        String expMsg = "(unknown)";
        Object td = h.getTestData();
        boolean isGv = false;
        if (td instanceof MergedFinanceArVoucherData) {
            MergedFinanceArVoucherData ar = (MergedFinanceArVoucherData) td;
            if (ar.getExpectedValidation() != null) expMsg = ar.getExpectedValidation().expectedMessage();
        } else if (td instanceof MergedFinanceApVoucherData) {
            MergedFinanceApVoucherData ap = (MergedFinanceApVoucherData) td;
            if (ap.getExpectedValidation() != null) expMsg = ap.getExpectedValidation().expectedMessage();
        } else if (td instanceof MergedFinanceGeneralVoucherData) {
            MergedFinanceGeneralVoucherData gv = (MergedFinanceGeneralVoucherData) td;
            if (gv.getExpectedValidation() != null) expMsg = gv.getExpectedValidation().expectedMessage();
            isGv = true;
        }
        // fintag (AP/AR) vs fincosttag (GV) — same reasoning as buildPositiveHtml's tag section.
        String lastRowLabel = isGv ? "DB — fincosttag" : "DB — fintag";
        return new String[][]{
            {"Field",           "Expected",                                          "Actual"},
            {"UI — Validation", expMsg,                                              "Asserted by scenario behavior"},
            {"DB — finvchmain", "No record created (save blocked by UI validation)", "0 records (not queried)"},
            {"DB — finvchdet",  "No record created",                                 "0 records (not queried)"},
            {"DB — finpost",    "No posting created",                                "0 entries (not queried)"},
            {lastRowLabel,      "No entries created",                                "0 entries (not queried)"},
        };
    }

    // ─── Table row / cell helpers ─────────────────────────────────────────────
    // Status/badge()/icon()/ICO_* all moved to FoldableSectionRenderer (plan §10) so
    // SnapshotValidationReporter's own Result/Status columns and section headers can share the
    // same badge pills and inline-SVG icons — see the static imports above.

    private static final String STATUS_COL = "Status";
    private static final String TD_BASE    = "padding:7px 12px;border-bottom:0.5px solid #E2E8F0;word-break:break-word";

    private static String tableOpen() {
        return "<div style='overflow-x:auto;width:100%'><table class='rs-tbl'>";
    }
    private static final String TABLE_CLOSE = "</table></div>";

    private static final String[] WIDTHS_4COL = {"18%", "36%", "36%", "10%"};

    private static String headerRow(String... cols) {
        StringBuilder sb = new StringBuilder("<thead><tr>");
        for (int i = 0; i < cols.length; i++) {
            String c = cols[i];
            boolean isStatus = STATUS_COL.equals(c);
            String wStyle = (cols.length == 4) ? "width:" + WIDTHS_4COL[i] + ";" : "";
            sb.append("<th style='font-size:11px;font-weight:500;color:#64748B;"
                    + "padding:6px 12px;background:#fff;border-bottom:0.5px solid #E2E8F0;"
                    + wStyle
                    + (isStatus ? "text-align:center" : "text-align:left") + "'>")
              .append(esc(c)).append("</th>");
        }
        return sb.append("</tr></thead><tbody>").toString();
    }

    /** Appends one row per UI-contract check ({@code h.getUiValidationResults()}) whose component
     *  name starts with any of {@code componentPrefixes} — merges style/state/label contract-check
     *  results directly under the matching field's own business-match row, instead of a separate
     *  section, per explicit user request (reuses the unmodified {@link #row} so the page's own
     *  top-level pass/fail tally keeps counting these too). No-op when {@code uiValidationResults}
     *  is null (flows that never call {@code validateUiContract}, e.g. Revert/Delete) or when no
     *  result matches the given prefixes (e.g. "Reference" has no contract check yet). */
    private static void appendUiContractRows(StringBuilder sb, ActualResultsHolder h, String... componentPrefixes) {
        if (h.getUiValidationResults() == null) return;
        for (UiValidationFailure f : UiValidationReporter.rowsFor(h.getUiValidationResults(), componentPrefixes)) {
            if (!f.path().isEmpty()) continue; // row/popup-scoped results render in their own section
            sb.append(uiContractRow(f));
        }
    }

    /** One Actual Results row for a UI-contract check — colour properties render with a swatch
     *  (via {@link UiValidationReporter#colorAwareValue}, already-safe HTML, so not re-escaped). */
    private static String uiContractRow(UiValidationFailure f) {
        return uiContractRow(f, false);
    }

    /** @param leafLabel {@code true} inside the nested tree, where the path is already shown by the
     *         enclosing nodes — the label then omits the "Row N / ..." prefix. */
    private static String uiContractRow(UiValidationFailure f, boolean leafLabel) {
        return uiContractRow(f, leafLabel, false);
    }

    /** @param propertyOnly the component is shown by a group heading row, so the Field cell is just the property */
    private static String uiContractRow(UiValidationFailure f, boolean leafLabel, boolean propertyOnly) {
        // A known issue keeps its real expected/actual and shows its note under the actual value.
        String actualHtml = UiValidationReporter.colorAwareValue(f.property(), f.actual())
                + (f.isKnownIssue()
                ? "<div style='font-size:11px;color:#E65100;margin-top:2px'>" + esc(f.note()) + "</div>" : "");
        return rowHtml(propertyOnly ? "↳ " + UiValidationReporter.propertyLabel(f)
                        : leafLabel ? UiValidationReporter.leafLabel(f) : UiValidationReporter.fieldLabel(f),
                UiValidationReporter.colorAwareValue(f.property(), f.expected()),
                actualHtml,
                f.expected(), f.actual(), UiValidationReporter.toRendererStatus(f.status()));
    }

    /** Tree node for {@link #buildScopedUiContractSection}: one section / row / popup / fill phase. */
    private static final class UiNode {
        final String name;
        final java.util.Map<String, UiNode> children = new java.util.LinkedHashMap<>();
        final List<UiValidationFailure> direct = new ArrayList<>();
        UiNode(String name) { this.name = name; }
        void collect(List<UiValidationFailure> out) {
            out.addAll(direct);
            children.values().forEach(c -> c.collect(out));
        }
    }

    /** UI-contract checks created inside a {@code UiValidationScope} node (grid rows, Allocate Sites,
     *  Payment Information, grid footer), rendered as the SAME hierarchy the Extent "UI Full Validation
     *  Details" tree uses — section &gt; Row N &gt; Allocate Sites / Payment Information &gt; Before /
     *  After fill — so each check sits under the grid row it belongs to instead of in one flat list.
     *  Expected values are untouched (they still come from the Excel style baseline via the results);
     *  this only changes how the already-produced results are laid out. Empty string when none. */
    private static String buildScopedUiContractSection(ActualResultsHolder h) {
        if (h.getUiValidationResults() == null) return "";
        List<UiValidationFailure> scoped = h.getUiValidationResults().stream()
                .filter(f -> !f.path().isEmpty()).toList();
        if (scoped.isEmpty()) return "";

        UiNode root = new UiNode("");
        for (UiValidationFailure f : scoped) {
            UiNode n = root;
            for (String segment : f.path()) n = n.children.computeIfAbsent(segment, UiNode::new);
            n.direct.add(f);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(FoldableSectionRenderer.sectionOpen("open", "rs-sub", "",
                FoldableSectionRenderer.headerLabel(ICO_DOC, "UI checks by section / row", 6)));
        UiValidationReporter.orderNodes(root.children.values(), n -> n.name, true)
                .forEach(top -> renderUiNode(sb, top, 0));
        sb.append(FoldableSectionRenderer.sectionClose());
        return sb.toString();
    }

    private static final java.util.Set<String> STYLE_PROPERTIES = java.util.Set.of(
            "font-size", "font-family", "font-weight", "color", "background-color", "border-color");

    /** A computed-style row of an ordinary field (section-header styles are never folded away). */
    private static boolean isPlainStyleRow(UiValidationFailure f) {
        String c = f.component() == null ? "" : f.component().toUpperCase();
        return STYLE_PROPERTIES.contains(f.property()) && !c.startsWith("SECTION_HEADER_");
    }

    private static void renderUiNode(StringBuilder sb, UiNode node, int depth) {
        List<UiValidationFailure> all = new ArrayList<>();
        node.collect(all);
        long fail = all.stream().filter(UiValidationFailure::isFail).count();
        long skip = all.stream().filter(UiValidationFailure::isSkip).count();
        long known = all.stream().filter(UiValidationFailure::isKnownIssue).count();
        long checked = all.size() - skip;
        String counts = checked + " checked, " + fail + " failed"
                + (known > 0 ? ", " + known + " known issue" + (known > 1 ? "s" : "") : "")
                + (skip > 0 ? ", " + skip + " skipped" : "");
        Status roll = fail > 0 ? Status.FAIL : known > 0 ? Status.KNOWN_ISSUE : checked == 0 ? Status.SKIP : Status.PASS;

        String header = "<span style='display:flex;align-items:center;gap:8px;width:100%'>"
                + "<span style='flex:1 1 auto;min-width:0;display:flex;align-items:center;gap:8px;overflow:hidden;"
                + "font-weight:500;text-transform:none;letter-spacing:0'>"
                + UiValidationReporter.titleWithChips(node.name) + "</span>"
                + "<span style='font-size:11px;color:#64748B;white-space:nowrap'>" + esc(counts) + "</span>"
                + badge(roll) + "</span>";
        // Open only where something needs attention; green branches stay collapsed.
        sb.append(FoldableSectionRenderer.sectionOpen(fail > 0 || known > 0 ? "open" : "", "rs-sub", "", header));
        if (!node.direct.isEmpty()) {
            sb.append(tableOpen());
            sb.append(headerRow("Field", "Expected (test input)", "Actual (UI)", "Status"));
            // One bold heading row per component, then only the property name per row — the component
            // name is never repeated (same grouping as UiValidationReporter's own tree).
            // Passing style rows (font / colour of every field) are summarised as ONE line per node - the
            // full evidence stays in "UI Full Validation Details". Style rows that fail / skip / are known
            // issues stay visible, and section-header colours are always listed (explicit user request).
            List<UiValidationFailure> shown = new ArrayList<>();
            int passedStyle = 0;
            for (UiValidationFailure f : node.direct) {
                if (isPlainStyleRow(f) && f.isPass()) { passedStyle++; } else { shown.add(f); }
            }
            for (java.util.Map.Entry<String, List<UiValidationFailure>> g
                    : UiValidationReporter.groupByComponent(shown).entrySet()) {
                sb.append("<tr style='background:#F8FAFC'><td colspan='4' style='").append(TD_BASE)
                  .append(";color:#1E293B;font-weight:600'>")
                  .append(esc(UiValidationReporter.displayName(g.getKey())))
                  .append(" <span style='font-weight:400;font-size:11px;color:#64748B'>&nbsp;")
                  .append(esc(UiValidationReporter.groupCounts(g.getValue()))).append("</span></td></tr>");
                for (UiValidationFailure f : g.getValue()) sb.append(uiContractRow(f, true, true));
            }
            if (passedStyle > 0) {
                sb.append(rowHtml("Style properties (" + passedStyle + ")", "per baseline",
                        "all match - see UI Full Validation Details", "per baseline", "match", Status.PASS));
            }
            sb.append("</tbody>").append(TABLE_CLOSE);
        }
        // Children sit in an indented, guide-lined block so the hierarchy is visible from position alone
        // (same treatment as the "UI Full Validation Details" tree).
        if (!node.children.isEmpty()) {
            sb.append("<div style='margin-left:14px;padding-left:8px;border-left:2px solid "
                    + (depth >= 2 ? "#E2E8F0" : "#CBD5E1") + "'>");
            UiValidationReporter.orderNodes(node.children.values(), n -> n.name, false)
                    .forEach(c -> renderUiNode(sb, c, depth + 1));
            sb.append("</div>");
        }
        sb.append(FoldableSectionRenderer.sectionClose());
    }

    /** Same as {@link #row} but {@code expectedHtml}/{@code actualHtml} are already-safe HTML (e.g. a
     *  colour swatch); the raw {@code expected}/{@code actual} strings feed only the tally. */
    private static String rowHtml(String field, String expectedHtml, String actualHtml,
                                  String expected, String actual, Status status) {
        tally(status, field, expected, actual);
        if (suppressed(status)) return "";
        String rowBg      = status == Status.FAIL ? "#FEF2F2" : status == Status.KNOWN_ISSUE ? "#FFF7ED" : "transparent";
        String actualColor = status == Status.FAIL ? "#991B1B" : status == Status.KNOWN_ISSUE ? "#E65100" : "#1E293B";
        StringBuilder sb = new StringBuilder();
        sb.append("<tr style='background:").append(rowBg).append("'>");
        sb.append("<td style='").append(TD_BASE).append(";color:#64748B;font-size:12px'>")
          .append(esc(field)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B'>")
          .append(expectedHtml).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:").append(actualColor).append("'>")
          .append(actualHtml).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";text-align:center'>")
          .append(badge(status)).append("</td>");
        sb.append("</tr>");
        return sb.toString();
    }

    private static String row(String field, String expected, String actual, Status status) {
        tally(status, field, expected, actual);
        if (suppressed(status)) return "";
        String rowBg      = status == Status.FAIL ? "#FEF2F2" : "transparent";
        String actualColor = status == Status.FAIL ? "#991B1B" : "#1E293B";
        StringBuilder sb = new StringBuilder();
        sb.append("<tr style='background:").append(rowBg).append("'>");
        sb.append("<td style='").append(TD_BASE).append(";color:#64748B;font-size:12px'>")
          .append(esc(field)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B'>")
          .append(esc(expected)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:").append(actualColor).append("'>")
          .append(esc(actual)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";text-align:center'>")
          .append(badge(status)).append("</td>");
        sb.append("</tr>");
        return sb.toString();
    }

    // Overload for sub-ledger header rows (variable columns, no status)
    private static String headerRow(String c1, String c2, String c3, String c4, String c5) {
        return headerRow(new String[]{c1, c2, c3, c4, c5});
    }
    private static String headerRow(String c1, String c2, String c3, String c4, String c5, String c6) {
        return headerRow(new String[]{c1, c2, c3, c4, c5, c6});
    }
    /** Row for the Flags section — 6 columns: Field | SL Name | SL ID | Expected | Actual (UI) | Status */
    private static String flagRow(String field, String slName, String slId,
                                  String expected, String actual, Status status) {
        tally(status, field, expected, actual);
        if (suppressed(status)) return "";
        String rowBg      = status == Status.FAIL ? "#FEF2F2" : "transparent";
        String actualColor = status == Status.FAIL ? "#991B1B" : "#1E293B";
        StringBuilder sb = new StringBuilder();
        sb.append("<tr style='background:").append(rowBg).append("'>");
        sb.append("<td style='").append(TD_BASE).append(";color:#64748B;font-size:12px'>")
          .append(esc(field)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B;font-size:12px'>")
          .append(esc(slName)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B;font-family:monospace;font-size:12px'>")
          .append(esc(slId)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:#1E293B'>")
          .append(esc(expected)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";color:").append(actualColor).append("'>")
          .append(esc(actual)).append("</td>");
        sb.append("<td style='").append(TD_BASE).append(";text-align:center'>")
          .append(badge(status)).append("</td>");
        sb.append("</tr>");
        return sb.toString();
    }

    private static String td(String content) {
        return "<td style='" + TD_BASE + ";color:#1E293B'>" + content + "</td>";
    }

    // ─── Status match helpers ─────────────────────────────────────────────────

    /** SKIP when {@code actual} is the {@link #NOT_REACHED} sentinel, else PASS/FAIL on an exact,
     *  trimmed string match. */
    private static Status matchExact(String actual, String expected) {
        if (actual.equals(NOT_REACHED)) return Status.SKIP;
        return actual.trim().equals(expected.trim()) ? Status.PASS : Status.FAIL;
    }

    /** Same SKIP-on-{@link #NOT_REACHED} rule as {@link #matchExact}, but PASS/FAIL on
     *  substring containment instead of an exact match — used where the actual value legitimately
     *  carries extra context (e.g. a resolved name/code suffix) around the expected value. */
    private static Status matchContains(String actual, String expected) {
        if (actual.equals(NOT_REACHED)) return Status.SKIP;
        return actual.contains(expected) ? Status.PASS : Status.FAIL;
    }

    // ─── Lookup helpers ───────────────────────────────────────────────────────

    /** Resolves {@code header}'s owner site code to a "code (name)" display string for the DB
     *  Validation section; {@link #NOT_REACHED} when {@code header} is null. */
    private static String resolveOwnerSite(FinVchMain header) {
        if (header == null) return NOT_REACHED;
        try {
            AdmSiteDao dao  = DatabaseManager.getJdbi().onDemand(AdmSiteDao.class);
            AdmSite    site = dao.findByCode(header.getAdmsiteCodeOwner());
            String     name = site != null ? site.getName() : "unknown";
            return "admsite_code_owner = " + header.getAdmsiteCodeOwner() + "  (" + name + ")";
        } catch (Exception e) {
            return "admsite_code_owner = " + header.getAdmsiteCodeOwner() + "  (name lookup failed)";
        }
    }

    /** Resolves {@code header}'s Cash/Bank GL code to a "code (name)" display string for the DB
     *  Validation section; {@link #NOT_REACHED} when {@code header} is null. */
    private static String resolveCashBankGl(FinVchMain header) {
        if (header == null) return NOT_REACHED;
        try {
            FinGlDao dao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
            FinGl    gl  = dao.findByGlCode(header.getGlcode());
            String   nm  = gl != null ? gl.getGlname() : "unknown";
            return "glcode = " + header.getGlcode() + "  (" + nm + ")";
        } catch (Exception e) {
            return "glcode = " + header.getGlcode() + "  (name lookup failed)";
        }
    }

    /** Looks up a GL's display name by code for {@link #buildGlEntriesTable}/
     *  {@link #buildCostTagEntriesTable}; falls back to {@code "GL <code>"} when the lookup fails
     *  or finds nothing. */
    private static String resolveGlName(long glcode) {
        try {
            FinGlDao dao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
            FinGl    gl  = dao.findByGlCode(glcode);
            return gl != null ? gl.getGlname() : "GL " + glcode;
        } catch (Exception e) {
            return "GL " + glcode;
        }
    }

    // ─── Expected column extraction ───────────────────────────────────────────

    /**
     * Returns [ownerSite, cashBank, docDate, reference, remarks, slCount, tagExpected]
     * extracted from the testData snapshot stored in the holder.
     */
    private static String[] extractExpected(ActualResultsHolder h) {
        Object td = h.getTestData();
        if (td instanceof MergedFinanceArVoucherData) {
            MergedFinanceArVoucherData ar = (MergedFinanceArVoucherData) td;
            var doc = ar.getDocumentInformation();
            int slCount = ar.getSubLedgerInformationList() != null
                    ? ar.getSubLedgerInformationList().size() : 0;
            boolean hasDoc = ar.getSelectDocumentList() != null
                    && !ar.getSelectDocumentList().isEmpty();
            String tagExp = hasDoc
                    ? ar.getSelectDocumentList().size() + " entries"
                    : "skipped (ADVANCE_PAYMENT)";
            return new String[]{
                doc != null ? ns(doc.getOwnersite()) : "—",
                doc != null ? ns(doc.getCashbank())  : "—",
                doc != null && doc.getDocumentdate() != null
                        ? doc.getDocumentdate().format(DATE_FMT) : "—",
                doc != null ? ns(doc.getReference()) : "—",
                doc != null ? ns(doc.getRemarks())   : "—",
                String.valueOf(slCount),
                tagExp
            };
        } else if (td instanceof MergedFinanceApVoucherData) {
            MergedFinanceApVoucherData ap = (MergedFinanceApVoucherData) td;
            var doc = ap.getDocumentInformation();
            int slCount = ap.getSubLedgerInformationList() != null
                    ? ap.getSubLedgerInformationList().size() : 0;
            boolean hasDoc = ap.getSelectDocumentList() != null
                    && !ap.getSelectDocumentList().isEmpty();
            String tagExp = hasDoc
                    ? ap.getSelectDocumentList().size() + " entries"
                    : "skipped (ADVANCE_PAYMENT)";
            return new String[]{
                doc != null ? ns(doc.getOwnersite()) : "—",
                doc != null ? ns(doc.getCashbank())  : "—",
                doc != null && doc.getDocumentdate() != null
                        ? doc.getDocumentdate().format(DATE_FMT) : "—",
                doc != null ? ns(doc.getReference()) : "—",
                doc != null ? ns(doc.getRemarks())   : "—",
                String.valueOf(slCount),
                tagExp
            };
        } else if (td instanceof MergedFinanceGeneralVoucherData) {
            MergedFinanceGeneralVoucherData gv = (MergedFinanceGeneralVoucherData) td;
            var doc = gv.getDocumentInformation();
            int lineCount = gv.getExpenseIncomeDetailList() != null
                    ? gv.getExpenseIncomeDetailList().size() : 0;
            boolean hasSiteAllocation = gv.getSiteAllocationList() != null
                    && !gv.getSiteAllocationList().isEmpty();
            String tagExp = hasSiteAllocation
                    ? gv.getSiteAllocationList().size() + " entries"
                    : "skipped (no site-applicable rows)";
            return new String[]{
                doc != null ? ns(doc.getOwnersite()) : "—",
                doc != null ? ns(doc.getCashbank())  : "—",
                doc != null && doc.getDocumentdate() != null
                        ? doc.getDocumentdate().format(DATE_FMT) : "—",
                doc != null ? ns(doc.getReference()) : "—",
                doc != null ? ns(doc.getRemarks())   : "—",
                String.valueOf(lineCount),
                tagExp
            };
        }
        return new String[]{"—", "—", "—", "—", "—", "—", "—"};
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private static final String NOT_REACHED = "— (not reached)";

    /** Normalizes {@code null} AND blank/empty strings to "—" — both mean "no value," and
     *  {@code matchExact}/{@code matchContains} compare this rendered form directly, so leaving
     *  blank strings un-normalized caused a cosmetic false FAIL (e.g. Expected="" vs Actual="—"
     *  for an intentionally-blank reference field) even when both sides meant the same thing. */
    private static String ns(Object v) {
        if (v == null) return "—";
        String s = v.toString();
        return s.isBlank() ? "—" : s;
    }
    private static String esc(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─── Status legend — promoted to FoldableSectionRenderer.STATUS_LEGEND (Round 3 Item E) ────

    // ─── Hide Extent timestamp script ─────────────────────────────────────────

    /**
     * Inline {@code <script>} appended once per render: hides the Status/Timestamp cells of the
     * Extent log row this widget's table lives in (so the wide report content isn't squeezed by
     * columns it doesn't use), and — once per page — injects a floating "Hide/Show Tests" button
     * that collapses the sidebar test list to give the (often very wide) Actual Results tables
     * more horizontal room.
     */
    private static final String HIDE_TS_SCRIPT =
        "<script>(function(){" +
        // ── Hide timestamp/status cells of the parent Extent log row ──────────
        // Use currentScript to find THIS script's exact <tr>, not the first .rs-wrap on page
        "var me=document.currentScript;" +
        "var tr=me?me.closest('tr'):null;" +
        "if(!tr){var w=document.querySelector('.rs-wrap');tr=w?w.closest('tr'):null;}" +
        "if(tr){var c=tr.cells;" +
        "for(var i=0;i<c.length-1;i++){c[i].style.cssText='display:none!important;width:0;padding:0'}" +
        "if(c.length>0)c[c.length-1].style.padding='0';}" +
        // ── Inject collapsible test-list panel toggle (once per page) ─────────
        "if(!document.body.dataset.rsTlDone){document.body.dataset.rsTlDone='1';" +
        "var s=document.createElement('style');" +
        "s.textContent=" +
        "`#rs-tl-btn{position:fixed;top:54px;z-index:9999;cursor:pointer;" +
        "background:#1F4E79;color:#fff;border:none;" +
        "border-radius:0 4px 4px 0;padding:5px 10px;" +
        "font-size:11px;font-weight:600;" +
        "box-shadow:2px 2px 8px rgba(0,0,0,.2);transition:left .2s}" +
        "#rs-tl-btn:hover{background:#163D63}" +
        ".rs-tl-col .test-list{display:none!important}" +
        ".rs-tl-col .test-content{max-width:100%!important;flex:1!important;width:100%!important}`;" +
        "document.head.appendChild(s);" +
        "var btn=document.createElement('button');" +
        "btn.id='rs-tl-btn';btn.title='Toggle test list';" +
        "function posBtn(col){" +
        "var tl=document.querySelector('.test-list');" +
        "var left=col||!tl?56:Math.round(tl.getBoundingClientRect().right);" +
        "btn.style.left=left+'px';" +
        "btn.innerHTML=col?'&#9654;&nbsp;Tests':'&#9664;&nbsp;Hide';}" +
        "btn.onclick=function(){" +
        "var tw=document.querySelector('.test-wrapper.test-view');" +
        "if(!tw)return;" +
        "tw.classList.toggle('rs-tl-col');" +
        "posBtn(tw.classList.contains('rs-tl-col'));};" +
        "document.body.appendChild(btn);" +
        "setTimeout(function(){posBtn(false);},150);}" +
        "})();</script>";

}
