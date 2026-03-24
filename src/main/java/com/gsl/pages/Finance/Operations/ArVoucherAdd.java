package com.gsl.pages.Finance.Operations;

import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherDocumentInformation;
import com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher.ARVoucherSubLedgerInformation;
import com.gsl.utils.Selenium.SeleniumUtils;

public class ArVoucherAdd implements CashBankParent {

    private ARVoucherDocumentInformation  arVoucherDocumentInformation;
    private ARVoucherSubLedgerInformation   arVoucherSubLedgerInformation;

    public ArVoucherAdd() {
        this.arVoucherDocumentInformation = new ARVoucherDocumentInformation(this);
        this.arVoucherSubLedgerInformation = new ARVoucherSubLedgerInformation();
    }

    // Delegator for fluent API
    public ArVoucherAdd clickCashBankSearchIcon() {
        arVoucherDocumentInformation.clickCashBankSearch();
        return this;
    }

    public ArVoucherAdd selectCashBank(String cashbankName) {
        return arVoucherDocumentInformation.selectCashBank(cashbankName);
    }

    public ArVoucherAdd setsubledgername(String subLedgerName) {
        arVoucherSubLedgerInformation.setSubLedgerName(subLedgerName);
        return this;
    }

    public ArVoucherAdd clickAdjust() {
        arVoucherSubLedgerInformation.clickAdjust();
        return this;
    }

    public ArVoucherAdd setSubLedgerAmount(String subLedgerAmount) throws InterruptedException {
        arVoucherSubLedgerInformation.setSubLedgerAmount(subLedgerAmount);
        return this;
    }



}
