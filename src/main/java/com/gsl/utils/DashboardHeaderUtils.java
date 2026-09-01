package com.gsl.utils;

import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Verifies that a module's dashboard panel has actually rendered its header with the expected
 * title, as opposed to trusting a page object purely because the loading mask cleared. See
 * {@code TopModuleMenuComponent.navigateToModule()} for the verify → refresh-recover → re-verify
 * flow this feeds into.
 */
public final class DashboardHeaderUtils {

    private DashboardHeaderUtils() {
    }

    /**
     * @throws IllegalStateException if the header never becomes visible within {@code timeoutSeconds},
     *                                or becomes visible with text that doesn't match {@code expectedTitle}.
     */
    public static void verifyDashboardHeader(By headerLocator, String expectedTitle, long timeoutSeconds) {
        WebElement el;
        try {
            // untilAnyVisible (not untilVisible) — the locator can transiently resolve to more
            // than one dashboardHeading node during a module transition; the naive first-match
            // wait timed out even while the correct header was visible among the other matches.
            el = WaitUtils.untilAnyVisible(headerLocator, timeoutSeconds);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Dashboard header (" + headerLocator
                    + ") never became visible within " + timeoutSeconds + "s (expected title '"
                    + expectedTitle + "').", e);
        }
        String actual = el.getText();
        if (!expectedTitle.equalsIgnoreCase(actual == null ? null : actual.trim())) {
            throw new IllegalStateException("Dashboard header found but text mismatch — expected '"
                    + expectedTitle + "', actual '" + actual + "'.");
        }
    }
}
