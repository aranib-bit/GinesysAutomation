package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ARVoucherFooter {
    private static final By SAVEBUTTON  = By.xpath("//span[text()='ave']");
    private static final By CLOSE_BUTTON = By.xpath("//*[@class='mdi mdi-24px mdi-close']");

    public DocumentPopup clickSave() {
        System.out.println("Inside save button");
        SeleniumUtils.click(SAVEBUTTON, WaitType.CLICKABLE, "SAVEBUTTON");
        return new DocumentPopup();
    }

    /** Clicks the X to close the AR Voucher form; returns the discard-confirmation dialog */
    public DocumentMessageBox clickClose() {
        SeleniumUtils.click(CLOSE_BUTTON, WaitType.CLICKABLE, "AR_VOUCHER_CLOSE");
        JavascriptExecutorUtils.waitForExtJS();
        return new DocumentMessageBox();
    }
}
