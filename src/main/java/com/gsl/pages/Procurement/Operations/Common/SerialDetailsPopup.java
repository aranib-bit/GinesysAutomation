package com.gsl.pages.Procurement.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumActions;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import static com.gsl.utils.Selenium.SeleniumUtils.click;
import static com.gsl.utils.Selenium.SeleniumUtils.sendKeys;

public class SerialDetailsPopup {

    private static final By MFGDATE= By.xpath("//span[normalize-space(text())='Mfg. Date:']/ancestor::label/following-sibling::div//div//input[contains (@id,'datefield-') and contains(@id, '-inputEl')]");
    private static final By SERIALNO=By.xpath("//span[normalize-space(text())='Serial No:']/ancestor::label/following-sibling::div//div//input[contains (@id,'textfield-') and contains(@id, '-inputEl')]");
    private static final By ACCEPT=By.xpath("//div[normalize-space(.)='Item Information']/ancestor::div[contains(@class,'x-window')]//span[contains(@id,'-btnInnerEl') and normalize-space(string(.))='Accept']");


    SerialDetailsPopup setMfgDate(String mfgdate) {
        sendKeys(MFGDATE, mfgdate, "MFGDATE");
        return this;
    }

    SerialDetailsPopup setSerialNo(String serialNo) {
        sendKeys(SERIALNO, serialNo, "SERIALNO");
        SeleniumActions.sendKeyToElement(SERIALNO, Keys.ENTER);
        return this;
    }

    ScanItemPage clickAccept(){
        click(ACCEPT, WaitType.CLICKABLE,"ACCEPT");
        return new ScanItemPage();
    }
}
