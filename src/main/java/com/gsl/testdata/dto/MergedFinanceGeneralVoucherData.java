package com.gsl.testdata.dto;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome;
import com.gsl.reports.GeneralVoucherIncomeExpenseRowView;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.DocumentSearchStrategy;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditEntryMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Merged General Voucher test data — the GV analogue of
 * {@code MergedFinanceApVoucherData}/{@code MergedFinanceArVoucherData}.
 *
 * <p>Implements {@link VoucherReportData} via {@link GeneralVoucherIncomeExpenseRowView} — a small
 * reporting-only adapter wrapping each {@link ExpenseIncomeDetail} row, since that class
 * deliberately does not implement {@code VoucherSubLedgerRow} directly (see its Javadoc).
 * {@link #getSelectDocumentList()}/{@link #getPaymentInformationList()} return empty lists — GV
 * has neither concept — which the interface's own Javadoc explicitly sanctions.
 */
@Data
@Builder
@AllArgsConstructor
public class MergedFinanceGeneralVoucherData
        implements com.gsl.testdata.StampableInvocationData, PostSaveDialogHandler.PostSaveSpec, VoucherReportData,
        com.gsl.database.ReservedDocumentKeyAware {

    private MasterTestData masterTestData;

    private DocumentInformation documentInformation;

    private List<ExpenseIncomeDetail> expenseIncomeDetailList;

    private List<SiteAllocation> siteAllocationList;

    // General Voucher has no per-invoice payment-info-check concept observed in the
    // screenshots (no Selection/Instrument-No pattern like AP/AR) — default false
    // until the live dialog is confirmed (see plan's "Verification / Open items").
    @Builder.Default
    private boolean needsPaymentInfoWarning = false;

    /** General Voucher has no TDS concept — always false, so PostSaveDialogHandler's tdsFlow never runs. */
    @Override
    public boolean isTdsProvided() {
        return false;
    }

    /** Non-null only for negative/boundary scenarios; null for positive tests. */
    private NegativeValidation expectedValidation;

    /**
     * Non-null only for a Revert row that was deliberately claimed to hit a specific
     * non-success dialog outcome (e.g. a closed-accounting-period candidate, expected to produce
     * {@code CANNOT_REVERT_MONTH_CLOSED}) — {@code RevertGeneralVoucher} asserts the live
     * {@code confirmRevert()} outcome matches this exactly, rather than only branching DB
     * validation on whatever outcome happened to occur. {@code null} for every ordinary positive
     * Revert row, where any of the live-confirmed outcomes is legal.
     */
    private VoucherRevertOutcome expectedRevertOutcome;

    /**
     * DB mode scenario label — human-readable variant id/name, set for every DB-mode row
     * (both placeholder and fully-resolved). {@code null} in Excel mode.
     */
    private String scenarioTypeLabel;

    /** DB mode scenario type. {@code null} in Excel mode. Empty-list detection on
     *  {@link #expenseIncomeDetailList} (not this field) is what signals a placeholder —
     *  see {@code DbGeneralVoucherDataSource}. */
    private GeneralVoucherScenarioType scenarioType;

    /** DB mode combination-matrix query parameters for this row. {@code null} in Excel mode. */
    private GeneralVoucherFixtureOptions fixtureOptions;

    /** Dimensional tags for {@code gv.tag} filtering — see {@link GeneralVoucherTag}. */
    @Builder.Default
    private Set<GeneralVoucherTag> tags = Collections.emptySet();

    /**
     * Payment info for the Cash/Bank counter-leg — set for CASH, CHEQUE, and NEFT_RTGS payment
     * variants (NONE deliberately carries none — see {@code GeneralVoucherPaymentVariant}'s class
     * Javadoc). For CHEQUE, whether this line behaves AP-style (Dr) or AR-style (Cr) is decided by
     * {@link #isCashBankPaymentDebit()}.
     */
    private PaymentInformation cashBankPaymentInfo;

    /** Round 23 — the REAL outcome of GeneralVoucherTemplateLoader's cheque resolution (as opposed
     *  to GeneralVoucherFixtureOptions#isKnownCheque()'s pre-decided INTENT) — null for every
     *  non-Cheque payment mode; SAME_BANK/DIFFERENT_BANK on a genuine finchqdet resolution; UNKNOWN
     *  both for a deliberately-unknown row AND for a "known" row whose resolver silently fell back
     *  (ChequeInstrumentResolver.resolveKnownSameBank found no available cheque for that GL) — the
     *  two are indistinguishable from a pure business-value perspective (both mean "no finchqdet
     *  row exists to resolve bookcode against"), which is exactly what GeneralVoucherCreateChangeSets
     *  needs to know for its bookcode rule. */
    private com.gsl.utils.cheque.ChequeInstrumentResolver.ChequeBankMatch actualChequeBankMatch;

    /**
     * Round 6 — one payment per grid row that carries one, keyed by the row's {@code lineNo}
     * ({@link PaymentInformation#getLineNo()}); rows without an entry have no payment. {@code null}
     * for Excel/Edit/negative data that never set it — those keep using the single
     * {@link #cashBankPaymentInfo} on the first row (see {@link #paymentForLine}). For a Create built
     * by {@code GeneralVoucherTemplateLoader}, {@link #cashBankPaymentInfo} is the FIRST of these
     * (kept so Edit/Revert/loggers that only know one payment keep working).
     */
    private List<PaymentInformation> rowPayments;

    /** Round 6 — the real cheque/bank outcome of each CHEQUE row, keyed by {@code lineNo}
     *  (per-row counterpart of {@link #actualChequeBankMatch}); {@code null} when not set. */
    private java.util.Map<String, com.gsl.utils.cheque.ChequeInstrumentResolver.ChequeBankMatch> rowChequeBankMatches;

    /**
     * Set by a data source that could not produce this row's fixture (e.g. the API Edit source could not create or read the voucher a
     * variant needs): the row is kept so the variant stays visible in the report, and the test fails on it by name instead of the row
     * silently disappearing. {@code null} for every ordinary row.
     */
    private String fixtureFailure;

    /** Structured log of DB data-gathering steps, rendered before test steps begin. */
    @Builder.Default
    private List<String> dbGatheringLog = new ArrayList<>();

    /**
     * The {@code scheme_docno} of an existing General Voucher to operate on.
     * Populated by Revert data sources (Excel {@code RevertGeneralVoucherDataSource} today,
     * DB {@code DbGeneralVoucherRevertDataSource} later); {@code null} in Create mode.
     * Used by {@code RevertGeneralVoucher} to locate the voucher in the list page.
     * Mirrors {@code MergedFinanceArVoucherData#getExistingVoucherNo()}.
     */
    private String existingVoucherNo;

    /**
     * {@code release_status} of the voucher as read from {@code finvchmain}
     * (Excel mode: the optional "Release Status" column, defaulting to {@code "P"} — Revert is
     * only legal from Posted). {@code null} in Create mode. Mirrors
     * {@code MergedFinanceArVoucherData#getReleaseStatus()}.
     */
    private String releaseStatus;

    /**
     * The voucher's date BEFORE this edit, as read from {@code finvchmain.vchdt} at
     * data-source-build time. {@code null} in Create mode. Distinct from
     * {@link #documentInformation}'s {@code documentdate}, which already carries the NEW/target
     * date for Edit — this field exists purely to surface the ORIGINAL date on the Extent report
     * (see {@code GeneralVoucherExtentDescriptionLogger#appendExtraHeaderRows}, "Current Doc
     * Date" row).
     */
    private java.time.LocalDate currentDocDate;

    /** The header's Reference BEFORE this edit, as read from {@code finvchmain.refno} at
     *  data-source-build time — same rationale/lifecycle as {@link #currentDocDate}, added (plan
     *  §9a) so the report can state a precise old→new value instead of just naming which field
     *  changed. {@code null} in Create mode. */
    private String originalReference;

    /** The header's Remarks BEFORE this edit, as read from {@code finvchmain.nartext} at
     *  data-source-build time — same rationale/lifecycle as {@link #currentDocDate}/
     *  {@link #originalReference}. {@code null} in Create mode. */
    private String originalRemarks;

    /** Plan §15a — the exact key this row's document was reserved under (i.e. the same string
     *  passed to {@code DocumentReservationRegistry.tryReserve}, {@code String.valueOf(vchcode)}),
     *  set only by {@code DbGeneralVoucherEditDataSource} (which claims ALL of a method's rows
     *  upfront, unlike single-row data sources). Lets {@code WebTest.tearDown} release only THIS
     *  row's own document at its own teardown, instead of the whole thread's reservation set
     *  (which used to prematurely release still-pending sibling rows after just the first one
     *  finished — see {@link com.gsl.database.ReservedDocumentKeyAware}). {@code null} for
     *  Create rows and for any row whose data source doesn't use the registry this way.
     */
    private String reservedDocumentKey;

    /**
     * Which UI operation this row drives — {@code CREATE}/{@code REVERT}/{@code EDIT}. Replaces
     * the fragile "{@code existingVoucherNo != null} ⇒ reverted" inference
     * {@code GeneralVoucherDescriptionBuilder} used to make (see
     * {@link GeneralVoucherOperationMode}'s Javadoc) — set by every data source going forward;
     * {@code null} on any row built before this field existed is treated as CREATE-or-REVERT by
     * the description builder's existing {@code existingVoucherNo}-based fallback, so older
     * Create/Revert data sources that don't set it keep working unchanged.
     */
    private GeneralVoucherOperationMode operationMode;

    /**
     * The existing Expense/Income Detail row (by {@code LineNo}) a detail-line Edit scenario
     * targets, plus its new values — {@code null} for a header-only edit, and for every
     * Create/Revert row. Mirrors {@link #existingVoucherNo}/{@link #releaseStatus} in being
     * Edit-only.
     */
    private DetailLineEditTarget editedDetailLine;

    /**
     * The before/after Payment Information pair a Payment-Info Edit scenario targets — {@code
     * null} for every other scenario (HEADER_ONLY/DETAIL_LINE Edit, Create, Revert, Delete).
     * Mirrors {@link #editedDetailLine} in being Edit-only. {@link #cashBankPaymentInfo}/{@link
     * #actualChequeBankMatch} are ALSO set (to this target's {@code newPayment}/{@code
     * newBankMatch}) for this scenario so the shared Extent report header (see {@code
     * GeneralVoucherExtentDescriptionLogger#appendExtraHeaderRows}) renders the post-edit
     * instrument number with no special-casing there.
     */
    private PaymentInfoEditTarget paymentEditTarget;

    /** Plan §6 (following the report-review finding that the test description was scope-blind):
     *  which header field(s) a {@code HEADER_ONLY} edit actually changed — carried forward from
     *  {@link GeneralVoucherEditVariant#getHeaderFieldScope()} at data-generation time so
     *  {@code GeneralVoucherDescriptionBuilder} can name the isolated field instead of always
     *  saying "reference/remarks/date" regardless of scope. {@code ALL} default (same as the
     *  variant's own default) so every pre-existing call site/variant needs no change; Excel mode
     *  has no scope dimension and leaves this at {@code ALL}, correctly matching "every filled-in
     *  New-* cell applies" (see {@code ExcelGeneralVoucherEditDataSource}'s own note on why it
     *  doesn't need a parallel {@code HeaderFieldScope} hookup). Only meaningful for a
     *  {@code HEADER_ONLY} edit; ignored/unset for {@code DETAIL_LINE}. */
    @Builder.Default
    private GeneralVoucherEditVariant.HeaderFieldScope headerFieldScope = GeneralVoucherEditVariant.HeaderFieldScope.ALL;

    /**
     * Which live UI path opened the voucher for edit — {@code ACTIONS_EDIT} or
     * {@code DOUBLE_CLICK_EDIT} (plan §9.5). {@code null} for Create/Revert rows and for any
     * Edit row built before this field existed — {@code EditGeneralVoucher} treats a {@code null}
     * value as {@code ACTIONS_EDIT} (same null-safe-fallback convention as
     * {@link #operationMode}).
     */
    private VoucherEditEntryMethod entryMethod;

    /**
     * Which tier {@code VoucherListPageBase#selectDocumentFast}/{@code #doubleClickToEditFast}
     * starts its cascade at — {@code null} (the common case) means
     * {@link DocumentSearchStrategy#EXACT_MATCH}. A test only sets this explicitly when it
     * already knows an earlier tier won't help for that scenario; whichever tier it starts at,
     * failure still falls through to the remaining, more-reliable tiers (same null-safe-fallback
     * convention as {@link #entryMethod}).
     */
    private DocumentSearchStrategy searchStrategy;

    // Explicit overrides to prevent Lombok from generating conflicting getter signatures —
    // see MergedFinanceApVoucherData for the rationale (Lombok aborts getter generation on a
    // name clash between its inferred concrete return type and the interface's covariant one).
    @Override public DocumentInformation getDocumentInformation() { return documentInformation; }

    /**
     * Adapts each {@link ExpenseIncomeDetail} row through {@link GeneralVoucherIncomeExpenseRowView}
     * so it satisfies {@code VoucherSubLedgerRow} — see that adapter's Javadoc for why the wrap
     * lives here rather than on {@code ExpenseIncomeDetail} itself.
     */
    @Override
    public List<GeneralVoucherIncomeExpenseRowView> getSubLedgerInformationList() {
        return expenseIncomeDetailList == null ? Collections.emptyList()
                : expenseIncomeDetailList.stream().map(GeneralVoucherIncomeExpenseRowView::new).toList();
    }

    /** General Voucher has no document-adjustment concept — always empty, per the interface's own contract. */
    @Override
    public List<VoucherSelectDocRow> getSelectDocumentList() { return Collections.emptyList(); }

    /**
     * Wraps {@link #cashBankPaymentInfo} as a single-element list when set (the combination
     * matrix's {@code CASH} payment variant); empty otherwise — Excel mode and the 3 existing
     * negative fixtures never set {@link #cashBankPaymentInfo}, so they keep getting an empty
     * list exactly as before.
     */
    @Override
    public List<VoucherPaymentInfo> getPaymentInformationList() {
        if (rowPayments != null && !rowPayments.isEmpty()) return new ArrayList<>(rowPayments);
        return cashBankPaymentInfo == null ? Collections.emptyList() : List.of(cashBankPaymentInfo);
    }

    /** All payments of this voucher in row order — {@link #rowPayments} when set, else the single {@link #cashBankPaymentInfo}. */
    public List<PaymentInformation> allPayments() {
        if (rowPayments != null && !rowPayments.isEmpty()) return rowPayments;
        return cashBankPaymentInfo == null ? Collections.emptyList() : List.of(cashBankPaymentInfo);
    }

    /**
     * The payment of the grid row with {@code lineNo}, or {@code null} when that row carries none.
     * When {@link #rowPayments} is unset (Excel/Edit/negative data) the single {@link #cashBankPaymentInfo}
     * belongs to the first row — exactly the pre-Round-6 behaviour.
     */
    public PaymentInformation paymentForLine(String lineNo) {
        if (lineNo == null) return null;
        if (rowPayments != null) {
            return rowPayments.stream().filter(p -> lineNo.equalsIgnoreCase(p.getLineNo())).findFirst().orElse(null);
        }
        if (cashBankPaymentInfo == null || expenseIncomeDetailList == null || expenseIncomeDetailList.isEmpty()) return null;
        return lineNo.equalsIgnoreCase(expenseIncomeDetailList.get(0).getLineNo()) ? cashBankPaymentInfo : null;
    }

    /** Cheque/bank outcome of the row with {@code lineNo} — falls back to {@link #actualChequeBankMatch} for the first row. */
    public com.gsl.utils.cheque.ChequeInstrumentResolver.ChequeBankMatch chequeBankMatchForLine(String lineNo) {
        if (lineNo == null) return null;
        if (rowChequeBankMatches != null) return rowChequeBankMatches.get(lineNo);
        if (expenseIncomeDetailList == null || expenseIncomeDetailList.isEmpty()) return actualChequeBankMatch;
        return lineNo.equalsIgnoreCase(expenseIncomeDetailList.get(0).getLineNo()) ? actualChequeBankMatch : null;
    }

    /** Whether the row with {@code lineNo} is on the Debit side (that row's own side, not row 1's). */
    public boolean isPaymentDebit(String lineNo) {
        if (expenseIncomeDetailList != null && lineNo != null) {
            for (ExpenseIncomeDetail row : expenseIncomeDetailList) {
                if (lineNo.equalsIgnoreCase(row.getLineNo())) return row.isDebit();
            }
        }
        return isCashBankPaymentDebit();
    }

    private int invocationIndex;
    private int totalInvocations;

    @Override
    public int getInvocationIndex() {
        return invocationIndex;
    }

    @Override
    public int getTotalInvocations() {
        return totalInvocations;
    }

    /** Rows carrying Site Applicable = Yes (Type 2 or Type 3) — the ones needing the Allocate Sites popup. */
    public List<ExpenseIncomeDetail> getSiteApplicableRows() {
        if (expenseIncomeDetailList == null) return Collections.emptyList();
        return expenseIncomeDetailList.stream()
                .filter(row -> row.getResolvedGlType() != null && row.getResolvedGlType().isSiteApplicable())
                .toList();
    }

    /** {@link SiteAllocation} rows for a given {@code lineNo}. */
    public List<SiteAllocation> getSiteAllocationsForLine(String lineNo) {
        if (siteAllocationList == null || lineNo == null) return Collections.emptyList();
        return siteAllocationList.stream()
                .filter(sa -> lineNo.equalsIgnoreCase(sa.getLineNo()))
                .toList();
    }

    /**
     * Whether the Cash/Bank/Cheque counter-leg (the sole {@link ExpenseIncomeDetail} row for
     * every combination-matrix CHEQUE/NEFT_RTGS scenario — see {@link #cashBankPaymentInfo}) is
     * on the Debit side. Drives which Payment Information popup shape {@code
     * GeneralVoucherExpenseIncomeDetail#createPaymentPopup(boolean)} uses (Dr → AP-style, Cr →
     * AR-style) — confirmed live via idealdb that GV's cheque-book participation (bookcode/
     * finchqdet) only ever happens on the Dr side, exactly like AP Voucher; Cr lines behave like
     * AR Voucher instead (VG/02556/26-27 vs. VG/02597/26-27). Defaults to {@code true} (today's
     * AP-only behavior) when {@link #expenseIncomeDetailList} is empty/null, so any caller not yet
     * covered by this fix keeps its pre-fix behavior.
     */
    public boolean isCashBankPaymentDebit() {
        return expenseIncomeDetailList == null || expenseIncomeDetailList.isEmpty()
                || expenseIncomeDetailList.get(0).isDebit();
    }
}
