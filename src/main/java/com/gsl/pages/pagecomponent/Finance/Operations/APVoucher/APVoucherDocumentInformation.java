package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.Finance.Operations.APVoucher.OnAccountClearConfirmAction;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.General.VoucherDocumentFormLabel;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
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
        setDocumentDate(formatDate(docInfo.getDocumentdate()));
        setReference(docInfo.getReference());
        clickCashBankSearch();
        selectCashBank(docInfo.getCashbank());
        setOnAccount(docInfo.isOnAccount(), docInfo.getOnAccountConfirmAction());
        setRemarks(docInfo.getRemarks());
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
