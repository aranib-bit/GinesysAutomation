package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ARVoucherFooter {
    private static final By SAVEBUTTON=By.xpath("//span[text()='ave']");

    public DocumentPopup clickSave() {
        System.out.println("Inside save button");
        SeleniumUtils.click(SAVEBUTTON, WaitType.CLICKABLE,"SAVEBUTTON");

        return new DocumentPopup();
    }
}
