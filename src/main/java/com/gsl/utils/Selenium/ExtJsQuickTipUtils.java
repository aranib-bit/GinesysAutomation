package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

/**
 * Common utility for hovering over ExtJS elements and reading / validating
 * QuickTip (gsl_ToolTip) popups. Reusable throughout the Finance module.
 *
 * <p>The tooltip XPath targets the visible ExtJS QuickTips singleton:
 * class {@code gsl_ToolTip} (no trailing W) with {@code x-autocontainer-innerCt}
 * as the text container. Confirmed 1-of-1 in live DevTools.
 */
public final class ExtJsQuickTipUtils {

    private static final String QUICKTIP_INNER_XPATH =
            "//div[contains(@class,'x-tip') and contains(@class,'gsl_ToolTip')" +
            " and contains(@class,'x-border-box')" +
            " and not(contains(@style,'display: none'))]" +
            "//div[contains(@class,'x-autocontainer-innerCt')]";

    private ExtJsQuickTipUtils() {}

    /** Hover over the element located by the given By. */
    public static void hover(By locator) {
        WebElement el = WaitUtils.untilVisible(locator);
        new Actions(DriverManager.getDriver()).moveToElement(el).perform();
    }

    /** Hover over an already-located element. */
    public static void hover(WebElement element) {
        new Actions(DriverManager.getDriver()).moveToElement(element).perform();
    }

    /**
     * Wait for the QuickTip to become visible and return its full text.
     * {@code getText()} concatenates all child text nodes (bold labels + plain text).
     */
    public static String readText() {
        return WaitUtils.untilVisible(By.xpath(QUICKTIP_INNER_XPATH)).getText();
    }

    /**
     * Hover over the element at {@code locator}, wait for the QuickTip, return full text.
     */
    public static String hoverAndRead(By locator) {
        hover(locator);
        return readText();
    }

    /**
     * Hover, wait for QuickTip, assert ALL expected substrings are present,
     * return the actual full text (same contract as DocumentPopup.verifyErrorMessage).
     *
     * @param locator          By locator of the hover target
     * @param expectedSubstrings one or more substrings that must be present in the tooltip text
     * @return actual tooltip text (for use in ReportLogger.pass message)
     * @throws AssertionError if any expected substring is absent
     */
    public static String hoverAndVerify(By locator, String... expectedSubstrings) {
        String actual = hoverAndRead(locator);
        return verifyContains(actual, expectedSubstrings);
    }

    /**
     * Hover over a pre-located element, assert all substrings, return actual text.
     */
    public static String hoverAndVerify(WebElement element, String... expectedSubstrings) {
        hover(element);
        String actual = readText();
        return verifyContains(actual, expectedSubstrings);
    }

    /**
     * Verify an already-visible QuickTip (after a prior hover) contains all substrings.
     */
    public static String verifyVisible(String... expectedSubstrings) {
        String actual = readText();
        return verifyContains(actual, expectedSubstrings);
    }

    /**
     * Read the raw innerHTML of the visible QuickTip inner container.
     * Use this when text-level checks are insufficient (e.g. verifying bold markup).
     */
    public static String readInnerHtml() {
        return WaitUtils.untilVisible(By.xpath(QUICKTIP_INNER_XPATH))
                .getAttribute("innerHTML");
    }

    /**
     * Verify an already-visible QuickTip innerHTML contains all expected HTML substrings.
     * Call after {@link #hoverAndVerify} — the tooltip stays open, no second hover needed.
     *
     * @throws AssertionError if any expected HTML substring is absent
     */
    public static String verifyVisibleHtml(String... expectedHtmlSubstrings) {
        String actual = readInnerHtml();
        for (String expected : expectedHtmlSubstrings) {
            if (!actual.contains(expected)) {
                throw new AssertionError(
                        "QuickTip HTML expected to contain [" + expected + "] but got: [" + actual + "]");
            }
        }
        return actual;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String verifyContains(String actual, String[] expectedSubstrings) {
        for (String expected : expectedSubstrings) {
            if (!actual.contains(expected)) {
                throw new AssertionError(
                        "QuickTip expected to contain [" + expected + "] but got: [" + actual + "]");
            }
        }
        return actual;
    }
}
