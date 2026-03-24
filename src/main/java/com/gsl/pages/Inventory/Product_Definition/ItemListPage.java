package com.gsl.pages.Inventory.Product_Definition;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ItemListPage {

    private static final By ITEMADD= By.xpath("//div[text()='Item']/following::span[text()='Add'][1]");


    public ItemAdd clickAdd() {
        SeleniumUtils.click(ITEMADD, WaitType.CLICKABLE, "ITEMADD");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new ItemAdd();
    }

}
