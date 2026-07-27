package com.gsl.pages.pagecomponent.Admin.Organization;

import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Admin.Organization.OrganizationMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OrganizationSideMenuComponent {

    private static final String ORGANIZATIONSIDEMENU = "//span[text()='%s']";

    public void clickMenuItem(OrganizationMenuType menuType) {
        ensureGroupExpanded(menuType.getGroup(), menuType.getName());
        String xpath = String.format(ORGANIZATIONSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName(), LogCategory.SUB_MENU);
    }

    private void ensureGroupExpanded(String groupName, String leafItemName) {
        By leafLocator = By.xpath(String.format(ORGANIZATIONSIDEMENU, leafItemName));
        if (SeleniumUtils.isElementPresent(leafLocator, 2)) {
            ReportLogger.info(LogCategory.SUB_MODULE, groupName + " group is already open");
            return;
        }
        By groupLocator = By.xpath(String.format(ORGANIZATIONSIDEMENU, groupName));
        SeleniumUtils.click(groupLocator, WaitType.CLICKABLE, groupName, LogCategory.SUB_MODULE);
    }
}
