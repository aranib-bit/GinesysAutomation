package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.Finance.Operations.APVoucher.OnAccountClearConfirmAction;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.General.VoucherDocumentFormLabel;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.utils.Selenium.ExtJsUtils;
import com.gsl.utils.Selenium.SeleniumUtils;

/**
 * Document Information section for AP Voucher.
 * Extends the shared base for common fields (scheme, date, reference, cash/bank, remarks).
 * Adds the On Account radio button and handles the warning popup that appears when
 * On Account = Yes is selected.
 */
public class APVoucherDocumentInformation extends VoucherDocumentInformationBase {

    public APVoucherDocumentInformation(CashBankParent parentPage) {
        super(parentPage, VoucherDomain.AP);
    }

    /**
     * Fills the AP Voucher document header.
     * On Account is set after Cash/Bank (its natural position in the form) and before Remarks.
     * Selecting Yes triggers a warning popup — "OK, Go Ahead" is clicked automatically.
     */
    public APVoucherDocumentInformation fillDocumentHeader(DocumentInformation docInfo) {
        setDocScheme(docInfo.getVoucherno());
        ReportLogger.trace("[DocHeader] docScheme → input=" + docInfo.getVoucherno());
        setDocumentDate(formatDate(docInfo.getDocumentdate()));
        ReportLogger.trace("[DocHeader] documentDate → input=" + docInfo.getDocumentdate()
                + " formatted=" + formatDate(docInfo.getDocumentdate()));
        setReference(docInfo.getReference());
        // T-03 negative scenario NEG_BLANK_CASHBANK leaves Cash/Bank deliberately empty so Save
        // raises "Cash/Bank is required". Skip the picker entirely when no value is supplied;
        // positive scenarios always pass a non-blank Cash/Bank, so this is a no-op for them.
        String cashbank = docInfo.getCashbank();
        if (cashbank != null && !cashbank.isBlank()) {
            clickCashBankSearch();
            selectCashBank(cashbank);
            ReportLogger.trace("[DocHeader] cashBank → selected=" + cashbank);
        } else {
            ReportLogger.trace("[DocHeader] cashBank → skipped (blank/null — negative scenario)");
        }
        setOnAccount(docInfo.isOnAccount(), docInfo.getOnAccountConfirmAction());
        ReportLogger.trace("[DocHeader] onAccount → value=" + docInfo.isOnAccount()
                + " confirmAction=" + docInfo.getOnAccountConfirmAction());
        setRemarks(docInfo.getRemarks());
        return this;
    }

    /**
     * Minimal header for the "Add Row without Cash/Bank" validation (FIN-AP-ADD-NEG-01):
     * sets only Date → Voucher No → On Account (=Yes). Reference, Cash/Bank, and Remarks are
     * intentionally NOT filled — clicking Add Row next must raise "Please select cash/bank ledger first."
     */
    public APVoucherDocumentInformation fillHeaderForAddRowValidation(DocumentInformation docInfo) {
        setDocumentDate(formatDate(docInfo.getDocumentdate()));
        setDocScheme(docInfo.getVoucherno());
        setOnAccount(docInfo.isOnAccount(), docInfo.getOnAccountConfirmAction());
        return this;
    }

    /** Fills only the Cash/Bank field — used in recovery behaviors after dismissing an error popup. */
    public APVoucherDocumentInformation fillCashBankOnly(String cashbank) {
        if (cashbank != null && !cashbank.isBlank()) {
            clickCashBankSearch();
            selectCashBank(cashbank);
        }
        return this;
    }

    private void setOnAccount(boolean onAccount, OnAccountClearConfirmAction confirmAction) {
        if (onAccount) {
            ExtJsUtils.selectExtJsRadio(VoucherDocumentFormLabel.ON_ACCOUNT, "Yes");
        } else {
            ExtJsUtils.selectExtJsRadio(VoucherDocumentFormLabel.ON_ACCOUNT, "No");
        }
        OnAccountClearConfirmHandler.handleIfPresent(confirmAction);
    }

    public String getOnAccount() {
        return ExtJsUtils.getSelectedExtJsRadioLabel(VoucherDocumentFormLabel.ON_ACCOUNT);
    }
}
