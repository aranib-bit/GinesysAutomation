package com.gsl.pages.pagecomponent.Finance.Operations;

import com.gsl.enums.ISubModule;
import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OperationsSideMenuComponent {

    private static final String SIDE_MENU_ITEM = "//span[text()='%s']";

    public <T> T clickMenuItem(String name, Class<T> expectedPage) {
        By itemLocator = By.xpath(String.format(SIDE_MENU_ITEM, name));
        SeleniumUtils.click(itemLocator, WaitType.CLICKABLE, name, LogCategory.SUB_MENU);
        T page = instantiate(name, expectedPage);

        // SeleniumUtils.click above only confirms the element was clickable/clicked — not that the
        // click actually navigated anywhere. A click landing on a still-masked/splash-screen overlay
        // can register as "clicked" while the app stays on the previous page, silently — confirmed
        // live, run 20260901_151519, FIN-GV-EDIT-HEADER-01: the Finance Dashboard stayed open and no
        // page-header toolbar ever appeared within the normal wait. Where the returned page can
        // verify its own identity, give it one bounded retry — re-click the same item once and
        // re-verify — before letting a second failure propagate, rather than failing (or worse,
        // silently proceeding) on a single silently-swallowed click.
        if (page instanceof VerifiablePage verifiable) {
            try {
                verifiable.verifyOnThisPage();
            } catch (RuntimeException firstAttemptFailure) {
                Log.warn("OperationsSideMenuComponent: '{}' didn't land after the first click ({}) — "
                                + "retrying the click once", name, firstAttemptFailure.getClass().getSimpleName());
                SeleniumUtils.click(itemLocator, WaitType.CLICKABLE, name, LogCategory.SUB_MENU);
                verifiable.verifyOnThisPage(); // let this one's exception propagate if it fails again
            }
        }
        return page;
    }

    private static <T> T instantiate(String name, Class<T> expectedPage) {
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName()
                    + " for menu item '" + name + "'", e);
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
