package com.gsl.pages.pagecomponent.Admin.Organization;

import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Admin.Organization.OrganizationMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Drives Admin &gt; Organization's left side-menu — items grouped under collapsible group headers
 * (Hierarchy/Manage/Explore/Periods, per {@link OrganizationMenuType#getGroup()}). {@link
 * #clickMenuItem(OrganizationMenuType)} is the entry point: it first makes sure the item's own
 * group is expanded (see {@link #ensureGroupExpanded}) before clicking the leaf item itself, so
 * callers don't need to know or care whether the group was already open.
 */
public class OrganizationSideMenuComponent {

    private static final String ORGANIZATIONSIDEMENU = "//span[text()='%s']";

    /** Expands {@code menuType}'s group if it isn't already (see {@link #ensureGroupExpanded}), then clicks the leaf menu item. */
    public void clickMenuItem(OrganizationMenuType menuType) {
        ensureGroupExpanded(menuType.getGroup(), menuType.getName());
        String xpath = String.format(ORGANIZATIONSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName(), LogCategory.SUB_MENU);
    }

    /**
     * Expands the side-menu group named {@code groupName} by clicking its header — unless {@code
     * leafItemName} is already present, in which case the group is treated as already expanded and
     * nothing is clicked. A short 2-second presence probe for the leaf doubles as the "is this
     * group already open" check, avoiding a redundant (and potentially wrong, e.g.
     * collapsing-instead-of-expanding) click on a group header that's already expanded.
     */
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
