package com.gsl.pages.pagecomponent.Inventory.Product_Definition;


import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ProductDefinitionSideMenuComponent {
    private static final String PRODUCTDEFINTIONSIDEMENU = "//span[text()='%s']";

    public <T> T clickMenuItem(Product_DefintionMenuType menuType, Class<T> expectedPage) {
        ensureGroupExpanded(menuType.getGroup(), menuType.getName());
        String xpath = String.format(PRODUCTDEFINTIONSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName(), LogCategory.SUB_MENU);
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }

    private void ensureGroupExpanded(String groupName, String leafItemName) {
        By leafLocator = By.xpath(String.format(PRODUCTDEFINTIONSIDEMENU, leafItemName));
        if (SeleniumUtils.isElementPresent(leafLocator, 2)) {
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
            return;
        }
        By groupLocator = By.xpath(String.format(PRODUCTDEFINTIONSIDEMENU, groupName));
        SeleniumUtils.click(groupLocator, WaitType.CLICKABLE, groupName, LogCategory.SUB_MODULE);
    }
}