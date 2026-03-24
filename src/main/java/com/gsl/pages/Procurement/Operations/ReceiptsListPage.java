package com.gsl.pages.Procurement.Operations;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ReceiptsListPage {

    private static final By RECEIPTSADD = By.xpath("//span[text()='Add']");

    public GoodsReceiveChallanAdd clickAdd() {
        SeleniumUtils.click(RECEIPTSADD, WaitType.CLICKABLE, "RECEIPTSADD");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new GoodsReceiveChallanAdd();
    }
}
