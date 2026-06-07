package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherSubLedgerInformation;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.APVoucher.APVoucherTdsAndNetPayable;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;

import java.util.List;

public class ApVoucherAdd implements CashBankParent {

    private final APVoucherDocumentInformation  apVoucherDocumentInformation;
    private final APVoucherSubLedgerInformation apVoucherSubLedgerInformation;
    private final APVoucherTdsAndNetPayable      apVoucherTdsAndNetPayable;
    private final APVoucherFooter                apVoucherFooter;

    public ApVoucherAdd() {
        this.apVoucherDocumentInformation  = new APVoucherDocumentInformation(this);
        this.apVoucherSubLedgerInformation = new APVoucherSubLedgerInformation();
        this.apVoucherTdsAndNetPayable     = new APVoucherTdsAndNetPayable(VoucherDomain.AP);
        this.apVoucherFooter               = new APVoucherFooter();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ApVoucherAdd fillDocumentHeader(DocumentInformation docInfo) {
        apVoucherDocumentInformation.fillDocumentHeader(docInfo);
        return this;
    }

    public ApVoucherAdd fillSubLedgerRowsForAdvance(MergedFinanceApVoucherData data, boolean includeRetry) {
        apVoucherSubLedgerInformation.fillSubLedgerRowsForAdvance(data, includeRetry);
        return this;
    }

    public ApVoucherAdd fillSubLedgerRowsForDocument(MergedFinanceApVoucherData data, boolean includeRetry) {
        apVoucherSubLedgerInformation.fillSubLedgerRowsForDocument(data, includeRetry);
        return this;
    }

    /** Called only when {@code data.isTdsProvided() = true} (On Account = Yes scenario). */
    public ApVoucherAdd fillTdsAndNetPayable(MergedFinanceApVoucherData data) {
        apVoucherTdsAndNetPayable.fillTdsRows(data);
        return this;
    }

    @LogStep("Save AP Voucher")
    public DocumentPopup clickSave() {
        JavascriptExecutorUtils.setSubLedgerVerify("apvch_grid");
        JavascriptExecutorUtils.restoreAllGlFieldsFromCapture(DriverManager.getDriver(), "apvch_grid");
        apVoucherFooter.clickSave();
        return new DocumentPopup();
    }

    /** Clicks the X to close the AP Voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox close() {
        return apVoucherFooter.clickClose();
    }
}
