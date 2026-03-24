package com.gsl.pages.Inventory.Product_Definition;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class CategoryListPage {

    private static final By CATEGORYADD= By.xpath("//div[text()='Category']/following::span[text()='Add']");

    public CategoryAdd clickAdd() {
        SeleniumUtils.click(CATEGORYADD, WaitType.CLICKABLE, "CATEGORYADD");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new CategoryAdd();
    }
}
