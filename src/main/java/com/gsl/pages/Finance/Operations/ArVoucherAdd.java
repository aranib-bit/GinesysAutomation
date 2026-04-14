package com.gsl.pages.Finance.Operations;

import com.gsl.driver.DriverManager;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Procurement.Operations.GoodsReceiveChallanAdd;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherFooter;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherSubLedgerInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;

import java.util.List;

public class ArVoucherAdd implements CashBankParent {

    private ARVoucherDocumentInformation  arVoucherDocumentInformation;
    private ARVoucherSubLedgerInformation   arVoucherSubLedgerInformation;
    private ARVoucherFooter arVoucherFooter;

    public ArVoucherAdd() {
        this.arVoucherDocumentInformation = new ARVoucherDocumentInformation(this);
        this.arVoucherSubLedgerInformation = new ARVoucherSubLedgerInformation();
        this.arVoucherFooter=new ARVoucherFooter();
    }

    // Delegator for fluent API
    public ArVoucherAdd clickCashBankSearchIcon() {
        arVoucherDocumentInformation.clickCashBankSearch();
        return this;
    }

    public ArVoucherAdd setDocumentDate(String date) {
        arVoucherDocumentInformation.setDocumentDate(date);
        return this;
    }

    public ArVoucherAdd setReference(String reference) {
        arVoucherDocumentInformation.setReference(reference);
        return this;
    }

    public ArVoucherAdd setRemarks(String remarks) {
        arVoucherDocumentInformation.setRemarks(remarks);
        return this;
    }

    public ArVoucherAdd setDocScheme(String docScheme) {
        arVoucherDocumentInformation.setDocScheme(docScheme);
        return this;
    }

    public ArVoucherAdd selectCashBank(String cashbankName) {
        return arVoucherDocumentInformation.selectCashBank(cashbankName);
    }

    public ArVoucherAdd setsubledgername(String subLedgerName, String id, String referenceSite) {
        arVoucherSubLedgerInformation.setSubLedgerName(subLedgerName, id, referenceSite);
        JavascriptExecutorUtils.debugSubLedgerRecords(DriverManager.getDriver());
        return this;
    }

    /**
     * Sets the subledger name for the given row index.
     * Row 0 is the first row; call addSubLedgerRow() before each row > 0.
     */
    public ArVoucherAdd setsubledgername(String subLedgerName, String id, String referenceSite, int rowIndex) {
        arVoucherSubLedgerInformation.setSubLedgerName(subLedgerName, id, referenceSite, rowIndex);
        JavascriptExecutorUtils.debugSubLedgerRecords(DriverManager.getDriver());
        return this;
    }

    public ArVoucherAdd setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        arVoucherSubLedgerInformation.setSubLedgerReferenceSite(referenceSite, rowIndex);
        return this;
    }

    /**
     * Adds a new empty row to the subledger grid.
     * Call this before setsubledgername/setSubLedgerAmount for each row after the first.
     */
    public ArVoucherAdd addSubLedgerRow() {
        arVoucherSubLedgerInformation.addSubLedgerRow();
        return this;
    }

    public AdjustmentEntry clickAdjust(String subLedgerName, String referenceSite) {
        arVoucherSubLedgerInformation.clickAdjust(subLedgerName, referenceSite);
        AdjustmentEntry adjustmentEntry = new AdjustmentEntry(DriverManager.getDriver());
        adjustmentEntry.openAdjustedDocumentMenu();
        return adjustmentEntry;
    }

    public ArVoucherAdd setSubLedgerAmount(String subLedgerAmount) throws InterruptedException {
        arVoucherSubLedgerInformation.setSubLedgerAmount(subLedgerAmount);
        return this;
    }

    /**
     * Sets the subledger amount for the given row index.
     */
    public ArVoucherAdd setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        arVoucherSubLedgerInformation.setSubLedgerAmount(subLedgerAmount, rowIndex);
        return this;
    }

    public ArVoucherAdd handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {
        arVoucherSubLedgerInformation.handlePaymentInformation(subLedgerList, paymentList);
        return this;
    }

    public DocumentPopup clickSave()  {
        JavascriptExecutorUtils.setSubLedgerVerify();
        arVoucherFooter.clickSave();
        return new DocumentPopup();
    }




}
