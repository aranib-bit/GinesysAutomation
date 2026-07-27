package com.gsl.pages.pagecomponent.Inventory.Operations;

import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OperationsSideMenuComponent {
    private static final String OPERATIONSSIDEMENU = "//span[text()='%s']";

    public void clickMenuItem(Product_DefintionMenuType menuType) {
        ensureGroupExpanded(menuType.getGroup(), menuType.getName());
        String xpath = String.format(OPERATIONSSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName(), LogCategory.SUB_MENU);
    }

    private void ensureGroupExpanded(String groupName, String leafItemName) {
        By leafLocator = By.xpath(String.format(OPERATIONSSIDEMENU, leafItemName));
        if (SeleniumUtils.isElementPresent(leafLocator, 2)) {
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
            return;
        }
        By groupLocator = By.xpath(String.format(OPERATIONSSIDEMENU, groupName));
        SeleniumUtils.click(groupLocator, WaitType.CLICKABLE, groupName, LogCategory.SUB_MODULE);
    }
}
