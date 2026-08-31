package com.gsl.testdata.dto;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.reports.GeneralVoucherIncomeExpenseRowView;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
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
        implements InvocationData, PostSaveDialogHandler.PostSaveSpec, VoucherReportData,
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
     * Payment info for the Cash/Bank counter-leg — non-null only for
     * {@code GeneralVoucherPaymentVariant.CASH} today (Cheque/NEFT-RTGS are blocked pending a
     * UI-discovery spike, and NONE deliberately carries none — see
     * {@code GeneralVoucherPaymentVariant}'s class Javadoc).
     */
    private PaymentInformation cashBankPaymentInfo;

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
        return cashBankPaymentInfo == null ? Collections.emptyList() : List.of(cashBankPaymentInfo);
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
}
