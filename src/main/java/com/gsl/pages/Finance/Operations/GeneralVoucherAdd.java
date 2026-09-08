package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherFooter;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.PageHeaderUtils;

import java.util.List;

/**
 * Top-level facade page for creating/viewing a General Voucher — mirrors
 * {@code ApVoucherAdd}/{@code ArVoucherAdd}: composes page components,
 * method-chaining, returns {@code this}.
 */
public class GeneralVoucherAdd implements CashBankParent, VerifiablePage {

    private final GeneralVoucherDocumentInformation  generalVoucherDocumentInformation;
    private final GeneralVoucherExpenseIncomeDetail   generalVoucherExpenseIncomeDetail;
    private final GeneralVoucherFooter                generalVoucherFooter;

    public GeneralVoucherAdd() {
        this.generalVoucherDocumentInformation = new GeneralVoucherDocumentInformation(this);
        this.generalVoucherExpenseIncomeDetail  = new GeneralVoucherExpenseIncomeDetail();
        this.generalVoucherFooter               = new GeneralVoucherFooter();
    }

    /**
     * Confirms the browser is actually showing this voucher's Add/Edit form right now, via the
     * same live page-header signal {@code VoucherListGridHeaderBase} already uses for list pages
     * — added (Round 23) after a confirmed live race where {@code VoucherListPageBase}'s
     * Actions→Edit click registered as successful while the app silently stayed on the list page:
     * the failure only surfaced 30+ seconds later, deep inside the Edit form, as a confusing
     * "General ledger column not found" error (FIN-GV-EDIT-DETAIL-01, run 20260908_135507).
     * {@code GeneralVoucherAdd} serves both the Add flow ({@code clickAdd()}) and every Edit entry
     * path, so both header shapes are accepted — confirmed against a live screenshot of this exact
     * page ("Add: General Voucher"). Deliberately checks for the colon-qualified header, not a
     * bare {@code contains("General Voucher")} — the list page's own header ("General Vouchers",
     * plural, no colon) would otherwise false-positive-match and defeat the whole check.
     */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        boolean onAddOrEditForm = actual != null
                && (actual.equalsIgnoreCase("Add: General Voucher")
                    || actual.equalsIgnoreCase("Edit: General Voucher"));
        if (!onAddOrEditForm) {
            throw new IllegalStateException(
                    "Expected the General Voucher Add/Edit form to be open (page header "
                    + "'Add: General Voucher' or 'Edit: General Voucher') but the live page "
                    + "header reads '" + actual + "' instead.");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public GeneralVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        generalVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public GeneralVoucherDocumentInformation getDocumentInformation() {
        return generalVoucherDocumentInformation;
    }

    public GeneralVoucherAdd fillExpenseIncomeRows(MergedFinanceGeneralVoucherData data) {
        generalVoucherExpenseIncomeDetail.fillExpenseIncomeRows(data);
        return this;
    }

    public GeneralVoucherExpenseIncomeDetail getExpenseIncomeDetail() {
        return generalVoucherExpenseIncomeDetail;
    }

    /** Edits an existing Expense/Income Detail row in place — see
     *  {@code GeneralVoucherExpenseIncomeDetail#editRow}'s Javadoc for scope/limitations. */
    public GeneralVoucherAdd editExpenseIncomeRow(int rowIndex, ExpenseIncomeDetail newValues) {
        generalVoucherExpenseIncomeDetail.editRow(rowIndex, newValues);
        return this;
    }

    /** Appends and fills a brand-new Expense/Income Detail row — plan §4B's "add a new line"
     *  shape. See {@code GeneralVoucherExpenseIncomeDetail#addRow}'s Javadoc for scope. */
    public GeneralVoucherAdd addExpenseIncomeRow(int rowIndex, ExpenseIncomeDetail newRow,
            List<SiteAllocation> allocations) {
        generalVoucherExpenseIncomeDetail.addRow(rowIndex, newRow, allocations);
        return this;
    }

    /** Re-opens a row's Allocate Sites popup and applies a new split — plan §4B's
     *  "site-split-only" shape. See {@code GeneralVoucherExpenseIncomeDetail#reallocateSites}'s
     *  Javadoc, including its UNCONFIRMED live-DOM caveat. */
    public GeneralVoucherAdd reallocateExpenseIncomeRowSites(int rowIndex, List<SiteAllocation> newSiteAllocations) {
        generalVoucherExpenseIncomeDetail.reallocateSites(rowIndex, newSiteAllocations);
        return this;
    }

    /** Fills the Cash/Bank counter-leg's Payment Window — see {@code GeneralVoucherExpenseIncomeDetail
     *  #handlePaymentInformation} for the no-op cases (null / CASH). */
    public GeneralVoucherAdd handlePaymentInformation(PaymentInformation payment) {
        generalVoucherExpenseIncomeDetail.handlePaymentInformation(payment);
        return this;
    }

    @LogStep("Save General Voucher")
    public DocumentPopup clickSave() {
        return generalVoucherFooter.clickSave();
    }

    /** Clicks the X to close the General Voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox close() {
        return generalVoucherFooter.clickClose();
    }
}
