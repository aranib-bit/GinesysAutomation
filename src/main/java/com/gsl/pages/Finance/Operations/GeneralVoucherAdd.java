package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail;
import com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherFooter;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

/**
 * Top-level facade page for creating/viewing a General Voucher — mirrors
 * {@code ApVoucherAdd}/{@code ArVoucherAdd}: composes page components,
 * method-chaining, returns {@code this}.
 */
public class GeneralVoucherAdd implements CashBankParent {

    private final GeneralVoucherDocumentInformation  generalVoucherDocumentInformation;
    private final GeneralVoucherExpenseIncomeDetail   generalVoucherExpenseIncomeDetail;
    private final GeneralVoucherFooter                generalVoucherFooter;

    public GeneralVoucherAdd() {
        this.generalVoucherDocumentInformation = new GeneralVoucherDocumentInformation(this);
        this.generalVoucherExpenseIncomeDetail  = new GeneralVoucherExpenseIncomeDetail();
        this.generalVoucherFooter               = new GeneralVoucherFooter();
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
