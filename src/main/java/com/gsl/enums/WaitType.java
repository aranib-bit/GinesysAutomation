package com.gsl.enums;

/**
 * Selects which {@code ExpectedConditions} wait {@code WaitUtils.until(By, WaitType)} (used by
 * {@code SeleniumUtils.click}) applies to a locator before interacting with it.
 */
public enum WaitType {
    //SET OF CONSTANTS
    /** Wait for the element to be present in the DOM (not necessarily visible) — {@code
     *  WaitUtils#untilPresent}. This is also the dispatcher's default/fallback case. */
    PRESENCE,
    /** Wait for the element to be visible AND enabled/clickable — {@code WaitUtils#untilClickable}. */
    CLICKABLE,
    /** Wait for the element to be visible (not necessarily clickable) — {@code WaitUtils#untilVisible}. */
    VISIBLE;
}
