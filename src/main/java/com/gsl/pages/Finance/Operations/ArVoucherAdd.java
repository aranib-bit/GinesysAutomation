package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherSubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;

public class ArVoucherAdd implements CashBankParent {

    private final ARVoucherDocumentInformation  arVoucherDocumentInformation;
    private final ARVoucherSubLedgerInformation arVoucherSubLedgerInformation;
    private final ARVoucherFooter               arVoucherFooter;

    public ArVoucherAdd() {
        this.arVoucherDocumentInformation = new ARVoucherDocumentInformation(this);
        this.arVoucherSubLedgerInformation = new ARVoucherSubLedgerInformation();
        this.arVoucherFooter = new ARVoucherFooter();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ArVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        arVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public ArVoucherAdd fillSubLedgerRows(MergedFinanceArVoucherData data, boolean includeRetry) {
        arVoucherSubLedgerInformation.fillSubLedgerRows(data, includeRetry);
        return this;
    }

    @LogStep("Save AR Voucher")
    public DocumentPopup clickSave() {
        JavascriptExecutorUtils.setSubLedgerVerify("arvch_grid");
        arVoucherFooter.clickSave();
        return new DocumentPopup();
    }

    /** Clicks the X to close the AR Voucher form; returns the discard-confirmation dialog */
    public DocumentMessageBox close() {
        return arVoucherFooter.clickClose();
    }
}
