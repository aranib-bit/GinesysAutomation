package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankPopup;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ARVoucherDocumentInformation {

    private static final By CASHBANKSEARCH=By.xpath("//span[text()='Cash/Bank:']/ancestor::label/following-sibling::div //div[contains(@id,'gslpicklistfieldextended-') and contains(@id,'-trigger-picker')]");

    private CashBankParent parentPage;

    public ARVoucherDocumentInformation(CashBankParent parentPage) {
        this.parentPage = parentPage;
    }



    public CashBankPopup clickCashBankSearch(){
        SeleniumUtils.click(CASHBANKSEARCH,WaitType.CLICKABLE, "CASHBANKSEARCH");
        return new CashBankPopup(parentPage);
    }

    public ArVoucherAdd selectCashBank(String cashbankName) {
        CashBankPopup popup = new CashBankPopup(parentPage);
        return (ArVoucherAdd) popup.selectCashBank(cashbankName);
    }



}
