package com.gsl.pages.pagecomponent.Finance.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanGeneral;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class CashBankPopup {
    private static final By CASHBANKSEARCHBOX=By.xpath("//span[normalize-space()='Search:']/ancestor::label/following-sibling::div//input[contains(@id,'textfield') and contains(@id,'-inputEl')]");
    private static final String CASHBANKSELECTION="//div[@class='x-grid-cell-inner ' and normalize-space()='%s']";
    private static final By CASHBANKPOPUPOKBUTTON=By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'K']");
    private static final By CASHBANKPOPUPCANCELBUTTON=By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'ancel']");

    private final CashBankParent parentPage;

    public CashBankPopup(CashBankParent parentPage) {
        this.parentPage = parentPage;
    }

    public CashBankPopup sendCashBankSearch(String cashbank){
        SeleniumUtils.sendKeys(CASHBANKSEARCHBOX,cashbank,"CASHBANKSEARCHBOX");
        return this;
    }

    private By getCashBankSelection(String cashbank) {
        String xpath = String.format(CASHBANKSELECTION, cashbank);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    public CashBankParent selectCashBank(String cashbankname) {
        SeleniumUtils.sendKeys(CASHBANKSEARCHBOX, cashbankname, "CASHBANKSEARCHBOX");
        SeleniumUtils.click(getCashBankSelection(cashbankname), WaitType.CLICKABLE, "Selection");
        SeleniumUtils.click(CASHBANKPOPUPOKBUTTON, WaitType.CLICKABLE, "OK");

        return parentPage; // return back to whatever page started it
    }

    public CashBankParent cancel() {
        SeleniumUtils.click(CASHBANKPOPUPCANCELBUTTON, WaitType.CLICKABLE, "Cancel");
        return parentPage;
    }





}
