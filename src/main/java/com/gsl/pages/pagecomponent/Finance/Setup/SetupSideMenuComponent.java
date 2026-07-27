package com.gsl.pages.pagecomponent.Finance.Setup;

import com.gsl.enums.ISubModule;
import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class SetupSideMenuComponent {

    private static final String SIDE_MENU_ITEM = "//span[text()='%s']";

    public <T> T clickMenuItem(String name, Class<T> expectedPage) {
        By itemLocator = By.xpath(String.format(SIDE_MENU_ITEM, name));
        SeleniumUtils.click(itemLocator, WaitType.CLICKABLE, name, LogCategory.SUB_MENU);
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }

    public <T> T clickMenuItem(ISubModule item, Class<T> expectedPage) {
        ensureGroupExpanded(item.getGroup(), item.getName());
        return clickMenuItem(item.getName(), expectedPage);
    }

    private void ensureGroupExpanded(String groupName, String leafItemName) {
        By leafLocator = By.xpath(String.format(SIDE_MENU_ITEM, leafItemName));
        if (SeleniumUtils.isElementPresent(leafLocator, 2)) {
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
            return;
        }
        By groupLocator = By.xpath(String.format(SIDE_MENU_ITEM, groupName));
        SeleniumUtils.click(groupLocator, WaitType.CLICKABLE, groupName, LogCategory.SUB_MODULE);
    }
}
