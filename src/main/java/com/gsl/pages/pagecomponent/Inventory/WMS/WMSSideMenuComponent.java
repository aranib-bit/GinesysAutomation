package com.gsl.pages.pagecomponent.Inventory.WMS;

import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.WMS.WMSMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class WMSSideMenuComponent {

    private static final String MENU_ITEM = "//span[text()='%s']"; // Assuming a similar structure for side menus

    /**
     * Clicks on a menu item in the WMS side menu.
     * @param menuType The menu item to click on.
     */
    public void clickMenuItem(WMSMenuType menuType) {
        ensureGroupExpanded(menuType.getGroup(), menuType.getName());
        String xpath = String.format(MENU_ITEM, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName(), LogCategory.SUB_MENU);
    }

    private void ensureGroupExpanded(String groupName, String leafItemName) {
        By leafLocator = By.xpath(String.format(MENU_ITEM, leafItemName));
        if (SeleniumUtils.isElementPresent(leafLocator, 2)) {
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
            return;
        }
        By groupLocator = By.xpath(String.format(MENU_ITEM, groupName));
        SeleniumUtils.click(groupLocator, WaitType.CLICKABLE, groupName, LogCategory.SUB_MODULE);
    }
}
