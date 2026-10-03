package com.gsl.reports;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSubLedgerRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.validation.snapshot.finance.AuditStateSummary;
import com.gsl.validation.snapshot.finance.VoucherPersistenceMapFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * General Voucher Extent description logger.
 *
 * <p>Extends {@link VoucherExtentDescriptionLogger} but bypasses its generic AP/AR-shaped
 * sub-ledger table and payment-information table entirely ({@link #hasGenericSubLedgers()} /
 * {@link #supportsPaymentInformation()} both {@code false}) — GV structurally has neither
 * concept (see {@code GeneralVoucherIncomeExpenseRowView}'s Javadoc), so those columns rendered
 * blank for every row and the "Payment Information" table rendered a misleading
 * "(intentionally blank for this scenario)" placeholder for a concept that doesn't exist at
 * all, not one merely absent this scenario. Instead, GV renders its own dedicated
 * "Income/Expense Detail" table via {@link #logMiddleSections} with separate
 * {@code Expense(Dr)}/{@code Income(Cr)} columns (matching the Excel sheet's actual column
 * names) rather than one ambiguous merged "Amount (Dr/Cr)" column.
 *
 * <p>GV-specific positive Expected Results reference {@code fincosttag} (site allocation)
 * rather than AP/AR's {@code fintag} (adjustment tagging) — a structurally different
 * table/concept, not a renamed one.
 *
 * <p>Call the static entry point {@link #log(MergedFinanceGeneralVoucherData)} after lazy
 * loading — it delegates to the template-method base.
 */
public final class GeneralVoucherExtentDescriptionLogger
        extends VoucherExtentDescriptionLogger<MergedFinanceGeneralVoucherData> {

    private static final GeneralVoucherExtentDescriptionLogger INSTANCE = new GeneralVoucherExtentDescriptionLogger();

    private GeneralVoucherExtentDescriptionLogger() {}

    /** Static entry point — call-sites follow the same convention as AP/AR. */
    public static void log(MergedFinanceGeneralVoucherData data) {
        INSTANCE.render(data);
    }

    /** The Header table rows ({@code Field | Value} header first) exactly as the Extent report shows them. */
    public static List<String[]> headerRowsOf(MergedFinanceGeneralVoucherData data) {
        return INSTANCE.headerRows(data);
    }

    /**
     * The Expected Results rows exactly as the Extent report shows them, except the audit-trail line
     * is stated without the live audit-state DB lookup (safe to call from a TestNG listener thread;
     * the Extent path keeps the live lookup).
     */
    public static List<String[]> expectedRowsOf(MergedFinanceGeneralVoucherData data) {
        OFFLINE.set(Boolean.TRUE);
        try {
            return INSTANCE.expectedRows(data);
        } finally {
            OFFLINE.remove();
        }
    }

    /** {@code true} while {@link #expectedRowsOf} builds rows without any DB lookup. */
    private static final ThreadLocal<Boolean> OFFLINE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // ── Abstract implementations ───────────────────────────────────────────────

    @Override
    protected String loggerLabel() { return "GeneralVoucherExtentDescriptionLogger"; }

    /** Unreachable — {@link #hasGenericSubLedgers()} is {@code false}, so the base class never
     *  calls this or {@link #slAmount}. Required only to satisfy the abstract contract. */
    @Override
    protected String amountColumnHeader() { return "Amount (Dr/Cr)"; }

    @Override
    protected BigDecimal slAmount(VoucherSubLedgerRow sl) {
        var row = ((GeneralVoucherIncomeExpenseRowView) sl).getExpenseIncomeDetail();
        return row.isDebit() ? row.getExpenseDr() : row.getIncomeCr();
    }

    @Override
    protected void appendExtraConsoleHeaderLines(MergedFinanceGeneralVoucherData data, StringBuilder sb) {
        // GV has no extra flags beyond the shared Pmt Info Warning line — no On Account/TDS concept.
    }

    /**
     * Appends GV-specific Header rows: the identifying "Voucher No." for any operation that acts
     * on an existing voucher (Edit/Revert/Delete), Cheque payment-info rows when the counter-leg
     * is Cheque-mode, and (Edit-only) the Entry Method plus Current/Expected Doc Date
     * cross-reference to the fuller Document Context table.
     */
    @Override
    protected void appendExtraHeaderRows(MergedFinanceGeneralVoucherData data, List<String[]> rows) {
        // Same operationMode-first, existingVoucherNo-fallback check logPositiveExpected uses —
        // Edit also sets existingVoucherNo, so operationMode must be checked first; a null
        // operationMode (rows built before that field existed) falls back to the
        // existingVoucherNo-based inference the description builder has always used.
        boolean operatesOnExistingVoucher = data.getOperationMode() == GeneralVoucherOperationMode.EDIT
                || data.getOperationMode() == GeneralVoucherOperationMode.REVERT
                || data.getOperationMode() == GeneralVoucherOperationMode.DELETE
                || (data.getOperationMode() == null && data.getExistingVoucherNo() != null);

        // "Doc Scheme" (the base Header table's own row, sourced from documentInformation's
        // voucherno) is deliberately blank for Edit — see DbGeneralVoucherEditDataSource's
        // Javadoc: that field is left null so the UI's Voucher No. widget is never touched
        // during Edit. "Voucher No." here is a distinct, always-populated field: the actual
        // identifying document number being edited/reverted/deleted (existingVoucherNo), needed
        // since Doc Scheme can't stand in for it on these operation modes.
        if (operatesOnExistingVoucher) {
            rows.add(new String[]{ "Voucher No.", nullSafe(data.getExistingVoucherNo()) });
        }

        // Cheque payment-info rows — rendered for ALL FOUR GV tests (Create/Edit/Revert/Delete),
        // deliberately NOT gated to operationMode==EDIT: Cheque-mode payment info is a
        // document-level attribute independent of which operation touched the voucher (same
        // "single shared layer" philosophy this method already applies to the rows above). Blank/
        // omitted whenever the Cash/Bank counter-leg isn't Cheque-mode, or cashBankPaymentInfo
        // hasn't been resolved at all for this scenario (e.g. Revert/Delete's data sources never
        // populate it today).
        // Round 6: a multi-row voucher can carry several cheques — one pair of rows per cheque row,
        // labelled with the grid row; a single-cheque voucher keeps the original labels.
        List<PaymentInformation> chequePayments = data.allPayments().stream()
                .filter(p -> p.getPaymentMode() == PaymentSelectionType.CHEQUE).toList();
        boolean severalRows = data.getExpenseIncomeDetailList() != null && data.getExpenseIncomeDetailList().size() > 1;
        for (PaymentInformation cheque : chequePayments) {
            String suffix = severalRows ? " (Row " + cheque.getLineNo() + ")" : "";
            var match = severalRows ? data.chequeBankMatchForLine(cheque.getLineNo()) : data.getActualChequeBankMatch();
            rows.add(new String[]{ "Cheque No." + suffix, nullSafe(cheque.getInstrumentNo()) });
            rows.add(new String[]{ "Cheque Known/Unknown" + suffix, chequeKnownUnknownLabel(match) });
        }

        // Edit-only cross-reference to the fuller "Document Context" table EditGeneralVoucher
        // renders later (Step 5) — one row here is enough to link the two without duplicating
        // that table's DB-plumbing detail (vchcode, persistence-map tables, etc.) into Header.
        if (data.getOperationMode() == GeneralVoucherOperationMode.EDIT) {
            rows.add(new String[]{ "Entry Method",
                    data.getEntryMethod() != null ? data.getEntryMethod().name() : "ACTIONS_EDIT (default)" });

            // "Current Doc Date"/"Expected Doc Date" — Edit-only (Revert never changes the date,
            // so these would be misleading there). "Expected Doc Date" mirrors the value already
            // shown in the base Header table's "Doc Date" row (documentInformation's NEW/target
            // date) under a clearer label; "Current Doc Date" is the voucher's ORIGINAL date
            // (finvchmain.vchdt at data-source-build time) — surfaced together so a reviewer can
            // confirm at a glance that the edit's new date stays within the same accounting year
            // as the original (see AccountingPeriodResolver).
            rows.add(new String[]{ "Current Doc Date",
                    data.getCurrentDocDate() != null ? data.getCurrentDocDate().format(DATE_FMT) : "" });
            var doc = data.getDocumentInformation();
            rows.add(new String[]{ "Expected Doc Date",
                    doc != null && doc.getDocumentdate() != null ? doc.getDocumentdate().format(DATE_FMT) : "" });
        }
    }

    /**
     * Maps the resolver's live cheque-bank-match outcome ({@link MergedFinanceGeneralVoucherData
     * #getActualChequeBankMatch()}) to a Header-table display label. KNOWN rows also show which
     * bank-match subtype in parentheses for extra detail — SAME_BANK/DIFFERENT_BANK both mean "a
     * real registered cheque was resolved" from a pure business-value perspective (see that
     * field's own Javadoc), but the subtype is still useful context for a report reviewer.
     * {@code null} (payment info not yet resolved, or a non-Cheque payment mode) renders as blank
     * rather than a fabricated label.
     */
    private static String chequeKnownUnknownLabel(ChequeInstrumentResolver.ChequeBankMatch match) {
        if (match == null) return "";
        return switch (match) {
            case SAME_BANK -> "KNOWN (Same Bank)";
            case DIFFERENT_BANK -> "KNOWN (Different Bank)";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    /** Dispatches to the operation-specific Expected Results table builder (Create/Edit/Revert/
     *  Delete) based on {@code data.getOperationMode()}, with the same null-fallback inference
     *  {@link GeneralVoucherDescriptionBuilder} uses when that field predates the current data. */
    @Override
    protected String[][] positiveExpectedRows(MergedFinanceGeneralVoucherData data) {
        // Same null-fallback GeneralVoucherDescriptionBuilder uses for its actionVerb swap: check
        // operationMode==EDIT first (Edit also sets existingVoucherNo, so it must win before the
        // existingVoucherNo-based REVERT fallback below), then existingVoucherNo!=null for REVERT,
        // else CREATE. A null operationMode (rows built before that field existed) falls through
        // to the same existingVoucherNo-based inference the description builder always used.
        if (data.getOperationMode() == GeneralVoucherOperationMode.EDIT) {
            return positiveExpectedForEdit(data);
        }
        if (data.getOperationMode() == GeneralVoucherOperationMode.DELETE) {
            return positiveExpectedForDelete();
        }
        if (data.getExistingVoucherNo() != null) {
            return positiveExpectedForRevert();
        }
        return positiveExpectedForCreate(data);
    }

    /** Builds the Expected Results rows for a Create run: finvchmain/finvchdet/finpost/
     *  fincosttag row expectations plus the audit-trail expectation note. */
    private String[][] positiveExpectedForCreate(MergedFinanceGeneralVoucherData data) {
        int lineCount = data.getExpenseIncomeDetailList() != null ? data.getExpenseIncomeDetailList().size() : 0;
        int siteRowCount = data.getSiteApplicableRows() != null ? data.getSiteApplicableRows().size() : 0;

        List<String> auditTables = new ArrayList<>(List.of("finvchmain", "finvchdet", "finpost"));
        if (siteRowCount > 0) auditTables.add("fincosttag");

        return new String[][]{
            { "Expected Outcome",  "Details" },
            { "UI — Save",         "Document saved; Document No. displayed on form" },
            { "DB — finvchmain",   "1 record: ownersite, cashbank, voucherno, docdate, refno, remarks, vchtype='G'" },
            { "DB — finvchdet",    lineCount + " row(s): glcode, damount/camount (per row's isDebit()), isreversal" },
            { "DB — finpost",      "1 VMG + " + lineCount + " VDG posting entries, balanced (BLOCKING — test fails if absent)" },
            { "DB — fincosttag",   siteRowCount + " row(s) — site allocation tagging for Type2/Type3 rows (COLLECT — non-blocking)" },
            { "DB — Audit Trail",  buildAuditExpectationNote("INSERT", auditTables.toArray(new String[0])) },
        };
    }

    /**
     * States what audit-trail coverage is actually expected for this Save — and, crucially, makes
     * that conditional on whether audit is CURRENTLY active for each named table on this
     * environment (resolved live, the same {@link AuditStateSummary} mechanism Item AA/AH already
     * use elsewhere) rather than unconditionally claiming an aud2_* row always shows up. An
     * audit-inactive table is explicitly called out as skipped, not silently omitted — a reader
     * shouldn't have to guess whether "no audit row" for a table means "nothing happened" or
     * "audit isn't even on for it right now".
     */
    private static String buildAuditExpectationNote(String action, String... tables) {
        if (OFFLINE.get()) {
            // Workbook path: no DB round-trip from the listener thread - name the tables, state the condition.
            return "aud2_* " + action + " row expected for: " + String.join(", ", tables)
                    + " (where audit is active on the environment)";
        }
        AuditStateSummary state = AuditStateSummary.resolveFor(VoucherPersistenceMapFactory.build(VoucherDomain.GV));
        List<String> active = new ArrayList<>();
        List<String> inactive = new ArrayList<>();
        for (String table : tables) {
            (state.auditActiveTables().contains(table) ? active : inactive).add(table);
        }
        if (active.isEmpty()) {
            return "Audit currently inactive for " + String.join(", ", tables)
                    + " on this environment — no aud2_* " + action + " row expected, none checked.";
        }
        String note = "aud2_* " + action + " row expected for: " + String.join(", ", active);
        return inactive.isEmpty() ? note + "."
                : note + "; audit inactive (skipped, not checked) for: " + String.join(", ", inactive) + ".";
    }

    /** Builds the Expected Results rows for an Edit run — phrasing differs for a detail-line
     *  edit vs. a header-only edit (see {@link #headerChangeDetail}). */
    private String[][] positiveExpectedForEdit(MergedFinanceGeneralVoucherData data) {
        boolean isDetailLineEdit = data.getEditedDetailLine() != null;
        String finvchdetDetail = isDetailLineEdit
                ? "LineNo '" + data.getEditedDetailLine().getLineNo() + "' updated in place (damount/camount and/or isreversal); every other row unchanged"
                : "unchanged — this is a header-only edit";

        // These two rows now differ by scenario rather than a single generic "(whichever
        // changed)" string for every Edit run — since a detail-only scenario's data source
        // leaves refno/nartext/vchdt genuinely untouched (see DbGeneralVoucherEditDataSource/
        // ExcelGeneralVoucherEditDataSource), a reviewer can now tell from this table alone
        // whether a given run was scoped to the header or the detail line.
        String finvchmainDetail = isDetailLineEdit
                ? "refno/nartext/vchdt unchanged (this is a detail-only edit); counter-leg damount/camount moves by the same amount as the edited detail line, dataversion bumped"
                : "1 record updated: " + headerChangeDetail(data) + ", dataversion bumped";
        String finpostDetail = isDetailLineEdit
                ? "Only the matching per-line (VDG) posting entry for the edited line updates its damount/camount to the new amount; every other posting row unchanged"
                : "Matching narration/refno posting entries updated together with finvchmain";

        return new String[][]{
            { "Expected Outcome",  "Details" },
            { "UI — Save",         "Document updated in place; same Document No. redisplayed on form" },
            { "DB — finvchmain",   finvchmainDetail },
            { "DB — finvchdet",    finvchdetDetail },
            { "DB — finpost",      finpostDetail },
            { "DB — fincosttag",   "Row(s) deleted+reinserted under new surrogate code (site allocation recomputed on every Save for a cost-tag-applicable document)" },
            { "DB — Audit Trail",  buildAuditExpectationNote("UPDATE", "finvchmain", "finvchdet", "finpost", "fincosttag") },
        };
    }

    /** Plan §9b: states precisely which of refno/nartext/vchdt changed and to what value, instead
     *  of the old "(whichever of those actually changed)" placeholder — same old→new data
     *  {@code GeneralVoucherDescriptionBuilder.headerFieldScopePhrase} uses (originalReference/
     *  originalRemarks/currentDocDate vs. the new documentInformation), kept as a small separate
     *  helper here since this class already duplicates its own report-table phrasing rather than
     *  sharing builder-internal helpers across the two classes. */
    private static String headerChangeDetail(MergedFinanceGeneralVoucherData data) {
        var doc = data.getDocumentInformation();
        StringBuilder sb = new StringBuilder();
        String newRef = doc != null ? doc.getReference() : null;
        if (!java.util.Objects.equals(data.getOriginalReference(), newRef)) {
            appendClause(sb, "refno: '" + data.getOriginalReference() + "' → '" + newRef + "'");
        }
        String newRemarks = doc != null ? doc.getRemarks() : null;
        if (!java.util.Objects.equals(data.getOriginalRemarks(), newRemarks)) {
            appendClause(sb, "nartext: '" + data.getOriginalRemarks() + "' → '" + newRemarks + "'");
        }
        String newDate = doc != null && doc.getDocumentdate() != null
                ? doc.getDocumentdate().format(VoucherReportUtils.DATE_FMT) : null;
        String oldDate = data.getCurrentDocDate() != null
                ? data.getCurrentDocDate().format(VoucherReportUtils.DATE_FMT) : null;
        if (!java.util.Objects.equals(oldDate, newDate)) {
            appendClause(sb, "vchdt: " + oldDate + " → " + newDate);
        }
        return sb.length() > 0 ? sb.toString() : "no header field actually changed";
    }

    /** Builds the Expected Results rows for a Revert run — only {@code finvchmain.release_status}
     *  is expected to change; every other table is asserted unchanged. */
    private String[][] positiveExpectedForRevert() {
        return new String[][]{
            { "Expected Outcome",  "Details" },
            { "UI — Revert",       "release_status flips Posted ('P') → Un-posted ('U'); no-op if already 'U'" },
            { "DB — finvchmain",   "release_status updated only; every other column unchanged" },
            { "DB — finvchdet",    "no record changes expected" },
            { "DB — finpost",      "no record changes expected" },
            { "DB — fincosttag",   "no record changes expected" },
            // Only finvchmain actually changes on a Revert (release_status) — the others are
            // asserted unchanged above, so no audit row is expected for them either.
            { "DB — Audit Trail",  buildAuditExpectationNote("UPDATE", "finvchmain") },
        };
    }

    /** Builds the Expected Results rows for a Delete run (Revert-then-delete when the voucher
     *  started Posted). */
    private String[][] positiveExpectedForDelete() {
        return new String[][]{
            { "Expected Outcome",  "Details" },
            { "UI — Delete",       "Voucher removed from the list page; confirmation dialog dismissed" },
            { "DB — finvchmain",   "row deleted (unposted first via Revert if it started Posted)" },
            // Reworded to be audit-state-aware, not an unconditional claim — see
            // buildAuditExpectationNote's Javadoc. Scoped to finvchmain only: Item K (Round 3,
            // not yet implemented) explicitly flags finvchdet/finpost/fincosttag's own Delete
            // audit coverage as a separate follow-up, not yet asserted here.
            { "DB — Audit Trail",  buildAuditExpectationNote("DELETE", "finvchmain") },
        };
    }

    // ── Hooks ──────────────────────────────────────────────────────────────────

    @Override protected boolean hasGenericSubLedgers() { return false; }
    @Override protected boolean supportsPaymentInformation() { return false; }

    /**
     * Renders GV's own "Income/Expense Detail" table in place of the generic sub-ledger table
     * — GL, Sub Ledger (Type3 rows only), separate Expense(Dr)/Income(Cr) columns, and Is
     * Reversal. Bypasses {@code GeneralVoucherIncomeExpenseRowView} entirely: that adapter
     * exists solely to satisfy {@code VoucherSubLedgerRow} for the generic renderer, which this
     * table no longer routes through.
     */
    @Override
    protected void logMiddleSections(MergedFinanceGeneralVoucherData data) {
        List<ExpenseIncomeDetail> lines = data.getExpenseIncomeDetailList();
        DetailLineEditTarget editedLine = data.getEditedDetailLine();
        // Plan §4B/4A: an ADD_NEW_LINE target has no pre-edit counterpart in `lines` at all (it's
        // not on the voucher yet at data-gathering time) — appended as its own extra row, tagged,
        // rather than silently missing from this table.
        boolean addNewLine = editedLine != null && editedLine.getShape() == DetailLineEditTarget.EditShape.ADD_NEW_LINE;
        ExtentLogger.info("<b>Income/Expense Detail</b>");
        if ((lines == null || lines.isEmpty()) && !addNewLine) {
            ExtentLogger.infoTable(new String[][]{
                { "Line#", "GL", "Sub-Ledger", "Sub Ledger Id", "Expense(Dr)", "Income(Cr)", "Is Reversal", "Change (this Edit)" },
                { "—", "(intentionally blank for this scenario)", "", "", "", "", "", "" }
            });
            return;
        }
        int existingCount = lines != null ? lines.size() : 0;
        String[][] table = new String[1 + existingCount + (addNewLine ? 1 : 0)][8];
        table[0] = new String[]{ "Line#", "GL", "Sub-Ledger", "Sub Ledger Id", "Expense(Dr)", "Income(Cr)", "Is Reversal", "Change (this Edit)" };
        for (int i = 0; i < existingCount; i++) {
            ExpenseIncomeDetail r = lines.get(i);
            table[i + 1] = new String[]{
                nullSafe(r.getLineNo()), nullSafe(r.getGeneralLedger()), nullSafe(r.getSubLedger()),
                nullSafe(r.getSubLedgerId()),
                VoucherReportUtils.fmtAmount(r.getExpenseDr()),
                VoucherReportUtils.fmtAmount(r.getIncomeCr()),
                r.isReversalYes() ? "YES" : "NO",
                buildDetailLineChangeText(editedLine, r)
            };
        }
        if (addNewLine) {
            ExpenseIncomeDetail newRow = editedLine.getNewValues();
            table[existingCount + 1] = new String[]{
                nullSafe(newRow.getLineNo()), nullSafe(newRow.getGeneralLedger()), nullSafe(newRow.getSubLedger()),
                nullSafe(newRow.getSubLedgerId()),
                VoucherReportUtils.fmtAmount(newRow.getExpenseDr()),
                VoucherReportUtils.fmtAmount(newRow.getIncomeCr()),
                newRow.isReversalYes() ? "YES" : "NO",
                "(NEW LINE ADDED)"
            };
        }
        ExtentLogger.infoTable(table);
        logSiteAllocationTable(data);
        logPaymentInformationTable(data);
    }

    /**
     * Round 16 (user-requested): "Income/Expense Detail" showed ledger-level amounts only — a
     * site-applicable line's own per-site breakdown (Allocate Sites popup) never appeared anywhere
     * near it. The data was already available and already driving the UI fill
     * ({@code GeneralVoucherExpenseIncomeDetail.commitSiteAllocations}) — this was purely a
     * missing render call, not a missing capture. Follows the exact same flat,
     * Line#-keyed-table pattern already proven for AP's structurally-identical "one line ↔ many
     * nested picks" case ({@code ApVoucherExtentDescriptionLogger.logFifoTable}). Skipped entirely
     * when the voucher has no site allocations at all, so untouched (non-site) scenarios see no
     * new empty table.
     *
     * <p>Expected-side only, same as the rest of this table — whether a line's allocation was
     * actually committed (OK) vs. rolled back (Cancel) in an incomplete-allocation negative
     * scenario isn't tracked on this list; {@code ActualResultsLogger.buildSiteAllocationSection}
     * remains the place to see what actually landed.
     */
    private void logSiteAllocationTable(MergedFinanceGeneralVoucherData data) {
        List<SiteAllocation> allocations = data.getSiteAllocationList();
        if (allocations == null || allocations.isEmpty()) return;
        String[][] table = new String[1 + allocations.size()][3];
        table[0] = new String[]{ "Line#", "Site Name", "Amount" };
        for (int i = 0; i < allocations.size(); i++) {
            SiteAllocation r = allocations.get(i);
            table[i + 1] = new String[]{
                nullSafe(r.getLineNo()), nullSafe(r.getSiteName()), VoucherReportUtils.fmtAmount(r.getAmount())
            };
        }
        ExtentLogger.info("<b>Site Allocation (Expected)</b>");
        ExtentLogger.infoTable(table);
    }

    /**
     * Renders a dedicated "Payment Information" table — previously the only Payment Info ever
     * shown anywhere in the report was two flat Header rows ("Cheque No."/"Cheque Known/Unknown",
     * see {@link #appendExtraHeaderRows}), which for an Edit scenario showed only the NEW
     * instrument number with no before→after comparison, no Instrument Date, no Drawn On/Cheque
     * label, and no Payment Mode — even though those are exactly what a Payment-Info-Edit
     * scenario validates (report-review finding, run {@code 20260916_133846}). Follows the same
     * "own dedicated table, skip entirely when not applicable" pattern as
     * {@link #logSiteAllocationTable}.
     *
     * <p>Edit ({@link MergedFinanceGeneralVoucherData#getPaymentEditTarget()} non-null) renders an
     * old→new row per field, using the exact same {@link GeneralVoucherDescriptionBuilder
     * #diffPaymentInfo} comparison the report title's own sentence
     * ({@code GeneralVoucherDescriptionBuilder#buildPaymentInfoEditSummary}) is built from, so the
     * two can never disagree about what changed. Create/Revert/Delete render a single current-value
     * row from {@link MergedFinanceGeneralVoucherData#getCashBankPaymentInfo()}; skipped entirely
     * when that's {@code null} (CASH/NONE payment, or Revert/Delete's data sources that never
     * resolve it).
     */
    private void logPaymentInformationTable(MergedFinanceGeneralVoucherData data) {
        PaymentInfoEditTarget target = data.getPaymentEditTarget();
        if (target != null) {
            PaymentInformation before = target.getBeforePayment();
            PaymentInformation after = target.getNewPayment();
            GeneralVoucherDescriptionBuilder.PaymentInfoDiff diff = GeneralVoucherDescriptionBuilder.diffPaymentInfo(target);

            ExtentLogger.info("<b>Payment Information (Expected)</b>");
            ExtentLogger.infoTable(new String[][]{
                { "Field", "Before", "After", "Changed (this Edit)" },
                { "Payment Mode", nullSafe(before.getPaymentMode()), nullSafe(after.getPaymentMode()),
                        diff.modeChanged() ? "YES" : "NO" },
                { "Instrument No.", nullSafe(before.getInstrumentNo()), nullSafe(after.getInstrumentNo()),
                        diff.instrumentNoChanged() ? "YES" : "NO" },
                { "Instrument Date", nullSafe(before.getInstrumentDate()), nullSafe(after.getInstrumentDate()),
                        diff.instrumentDateChanged() ? "YES" : "NO" },
                { "Drawn On / Cheque Label", nullSafe(before.getDrawnOn()), nullSafe(after.getDrawnOn()), "NO" },
            });
            return;
        }

        List<PaymentInformation> payments = data.allPayments();
        if (payments.isEmpty()) return;

        ExtentLogger.info("<b>Payment Information (Expected)</b>");
        if (payments.size() == 1 && (data.getExpenseIncomeDetailList() == null
                || data.getExpenseIncomeDetailList().size() <= 1)) {
            PaymentInformation paymentInfo = payments.get(0);
            ExtentLogger.infoTable(new String[][]{
                { "Payment Mode", "Instrument No.", "Instrument Date", "Drawn On / Cheque Label", "Reference" },
                { nullSafe(paymentInfo.getPaymentMode()), nullSafe(paymentInfo.getInstrumentNo()),
                        nullSafe(paymentInfo.getInstrumentDate()), nullSafe(paymentInfo.getDrawnOn()),
                        nullSafe(paymentInfo.getReference()) },
            });
            return;
        }
        // Several grid rows: one line per row, "none" for a row that carries no payment.
        List<String[]> table = new ArrayList<>();
        table.add(new String[]{ "Row", "Payment Mode", "Instrument No.", "Instrument Date", "Drawn On / Cheque Label", "Reference" });
        for (ExpenseIncomeDetail line : data.getExpenseIncomeDetailList()) {
            PaymentInformation p = data.paymentForLine(line.getLineNo());
            table.add(p == null
                    ? new String[]{ nullSafe(line.getLineNo()), "none", "", "", "", "" }
                    : new String[]{ nullSafe(line.getLineNo()), nullSafe(p.getPaymentMode()), nullSafe(p.getInstrumentNo()),
                            nullSafe(p.getInstrumentDate()), nullSafe(p.getDrawnOn()), nullSafe(p.getReference()) });
        }
        ExtentLogger.infoTable(table.toArray(new String[0][]));
    }

    /**
     * Renders the old→new change for the one row a detail-line edit targets — blank for every
     * other row. Previously this table showed only the pre-edit amount with nothing indicating
     * which line was about to change or to what, which was confusing when verifying the edit
     * took effect (report-review finding). {@code beforeRow} comes from the pre-edit
     * {@code expenseIncomeDetailList} already logged above; {@code editedLine.getNewValues()}
     * carries the post-edit target — both already resolved by the data source, nothing new is
     * captured here.
     */
    private static String buildDetailLineChangeText(DetailLineEditTarget editedLine, ExpenseIncomeDetail beforeRow) {
        if (editedLine == null || editedLine.getLineNo() == null
                || !editedLine.getLineNo().equals(beforeRow.getLineNo())
                // ADD_NEW_LINE's row is rendered as its own extra "(NEW LINE ADDED)" row above —
                // its LineNo never matches an existing pre-edit row, but guard explicitly anyway.
                || editedLine.getShape() == DetailLineEditTarget.EditShape.ADD_NEW_LINE) {
            return "";
        }
        ExpenseIncomeDetail after = editedLine.getNewValues();
        StringBuilder sb = new StringBuilder();

        // Plan §4A: GL-type transition — state the GL change itself (name + account class, when
        // resolved), not just the amount that happens to move alongside it.
        if (!java.util.Objects.equals(beforeRow.getGeneralLedger(), after.getGeneralLedger())) {
            appendClause(sb, "GL: " + glLabel(beforeRow) + " → " + glLabel(after));
        }

        String beforeDr = VoucherReportUtils.fmtAmount(beforeRow.getExpenseDr());
        String afterDr = VoucherReportUtils.fmtAmount(after.getExpenseDr());
        String beforeCr = VoucherReportUtils.fmtAmount(beforeRow.getIncomeCr());
        String afterCr = VoucherReportUtils.fmtAmount(after.getIncomeCr());

        if (after.getExpenseDr() != null && !beforeDr.equals(afterDr)) {
            appendClause(sb, "Expense(Dr): " + beforeDr + " → " + afterDr);
        } else if (after.getIncomeCr() != null && !beforeCr.equals(afterCr)) {
            appendClause(sb, "Income(Cr): " + beforeCr + " → " + afterCr);
        }

        if (beforeRow.isReversalYes() != after.isReversalYes()) {
            appendClause(sb, "Is Reversal: " + (beforeRow.isReversalYes() ? "YES" : "NO")
                    + " → " + (after.isReversalYes() ? "YES" : "NO"));
        }

        // Plan §4A: for a Site-applicable line, SITE_SPLIT_ONLY is the one shape where the
        // Expense(Dr)/Income(Cr) diff above shows NO change (by design — newValues is an exact
        // copy of beforeRow) even though the line genuinely changed; state that explicitly rather
        // than leaving the row looking untouched.
        boolean siteApplicable = after.getResolvedGlType() != null && after.getResolvedGlType().isSiteApplicable();
        if (siteApplicable && editedLine.getShape() == DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY) {
            appendClause(sb, "Site allocation: same total amount — sitewise split redistributed");
        }

        return sb.length() > 0 ? sb.toString() : "(no change)";
    }

    private static void appendClause(StringBuilder sb, String clause) {
        if (sb.length() > 0) sb.append("; ");
        sb.append(clause);
    }

    /** GL name, suffixed with its resolved account class in brackets when known — mirrors
     *  {@link GeneralVoucherDescriptionBuilder#glLabel}'s own helper for this table's GL column. */
    private static String glLabel(ExpenseIncomeDetail row) {
        return row.getGeneralLedger() + (row.getResolvedAccountClass() != null
                ? " [" + row.getResolvedAccountClass().label() + "]" : "");
    }
}
