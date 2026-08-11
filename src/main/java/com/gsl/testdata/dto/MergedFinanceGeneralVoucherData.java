package com.gsl.testdata.dto;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.reports.GeneralVoucherIncomeExpenseRowView;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherSelectDocRow;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.NegativeValidation;
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
        implements InvocationData, PostSaveDialogHandler.PostSaveSpec, VoucherReportData {

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
