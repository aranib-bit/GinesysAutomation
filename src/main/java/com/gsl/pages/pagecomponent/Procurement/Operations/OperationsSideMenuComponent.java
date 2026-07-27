package com.gsl.pages.pagecomponent.Procurement.Operations;

import com.gsl.enums.ISubModule;
import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OperationsSideMenuComponent {

    private static final String SIDE_MENU_ITEM = ".//span[text()='%s']";

    private final By moduleRoot;

    public OperationsSideMenuComponent(By moduleRoot) {
        this.moduleRoot = moduleRoot;
    }

    public <T> T clickMenuItem(String name, Class<T> expectedPage) {
        String relativeXpath = String.format(SIDE_MENU_ITEM, name);
        By itemLocator = By.xpath(relativeXpath);
        SeleniumUtils.click(
                SeleniumUtils.findChildElement(moduleRoot, itemLocator),
                WaitType.CLICKABLE,
                name,
                LogCategory.SUB_MENU
        );
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
        try {
            SeleniumUtils.findChildElement(moduleRoot, leafLocator);
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
        } catch (Exception e) {
            By groupLocator = By.xpath(String.format(SIDE_MENU_ITEM, groupName));
            SeleniumUtils.click(
                    SeleniumUtils.findChildElement(moduleRoot, groupLocator),
                    WaitType.CLICKABLE,
                    groupName,
                    LogCategory.SUB_MODULE
            );
        }
    }
}
