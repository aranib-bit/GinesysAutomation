package com.gsl.pages.Procurement.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.LoginPage;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.click;
import static com.gsl.utils.Selenium.SeleniumUtils.sendKeys;

public class BatchDetailsPopup {

    private static final By BATCHNO =By.xpath("//input[contains(@id, 'gslpicklistfield-') and contains(@id, '-inputEl')]");
    private static final By MFGDATEPICKER=By.xpath("//span[normalize-space(text())='Mfg. Date:']/ancestor::label/following-sibling::div//div[contains(@id,'datefield-') and contains(@id,'-trigger-picker')]");
    private static final By MONTHYEARTEXT=By.xpath("//div[contains(@class,'x-datepicker-header')]//span[@data-ref='btnInnerEl' and contains(@id,'-btnInnerEl')]");
    private static final By ACCEPT=By.xpath("//div[normalize-space(.)='Item Information']/ancestor::div[contains(@class,'x-window')]//span[contains(@id,'-btnInnerEl') and normalize-space(string(.))='Accept']");
    private static final By MFGDATE=By.xpath("//span[normalize-space(text())='Mfg. Date:']/ancestor::label/following-sibling::div//div//input[contains (@id,'datefield-') and contains(@id, '-inputEl')]");

    BatchDetailsPopup setBatchNo(String batchno) {
        sendKeys(BATCHNO, batchno, "BATCHNO");
        return this;
    }

    BatchDetailsPopup setMfgDate(String mfgdate) {
        sendKeys(MFGDATE, mfgdate, "MFGDATE");
        return this;
    }

    ScanItemPage clickAccept(){
        click(ACCEPT, WaitType.CLICKABLE,"ACCEPT");
        return new ScanItemPage();
    }










}
