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
        // setCashBankField (base class) already has the blank-guard for T-03's NEG_BLANK_CASHBANK
        // scenario, the equals-guard against re-selecting an already-correct value, and now the
        // post-fill commit verification too — was previously duplicated here unconditionally.
        setCashBankField(docInfo.getCashbank());
        ReportLogger.trace("[DocHeader] cashBank → input=" + docInfo.getCashbank());
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
        setCashBankField(cashbank);
        return this;
    }

    private void setOnAccount(boolean onAccount, OnAccountClearConfirmAction confirmAction) {
        String target = onAccount ? "Yes" : "No";
        // Equals-guard — the highest-priority instance of this codebase-wide gap (see the
        // "Missing equals-guard" audit): toggling On Account fires a destructive "sub-ledger/TDS/
        // payment cleared" confirmation dialog, so re-selecting an already-correct value was not
        // just wasteful but actively risked firing that clear-warning against already-filled data
        // on any caller (e.g. an Edit flow) that passes the existing value through unchanged.
        String current = getOnAccount();
        if (current != null && current.equalsIgnoreCase(target)) {
            ReportLogger.trace("[DocHeader] onAccount already '" + current + "' — skipping update");
            return;
        }
        ExtJsUtils.selectExtJsRadio(VoucherDocumentFormLabel.ON_ACCOUNT, target);
        OnAccountClearConfirmHandler.handleIfPresent(confirmAction);
    }

    public String getOnAccount() {
        return ExtJsUtils.getSelectedExtJsRadioLabel(VoucherDocumentFormLabel.ON_ACCOUNT);
    }
}
