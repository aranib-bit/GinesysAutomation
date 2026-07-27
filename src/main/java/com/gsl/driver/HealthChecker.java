package com.gsl.driver;

import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/**
 * "Health Checker" — cheap, non-throwing probes of session/browser/page state, run BEFORE
 * {@link RecoveryManager} decides whether/how to recover. Never restart a browser blind —
 * check first.
 *
 * Most of this already exists as {@link WaitUtils} (mask/AJAX/layout waits); this class adds
 * the few checks that weren't already there: is the browser process itself still alive, and
 * is the current page actually the login page (i.e. the session died server-side).
 */
public final class HealthChecker {

    // Mirrors LoginPage.Loc.TXTBOX_USERNAME — duplicated rather than exposed, since LoginPage's
    // locators are intentionally private; this is a read-only presence probe, not a page action.
    private static final By LOGIN_USERNAME_FIELD = By.name("username");

    private HealthChecker() {}

    /** Cheapest possible probe — a dead/closed driver throws on any call, including getTitle(). */
    public static boolean isBrowserAlive() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) return false;
        try {
            driver.getTitle();
            return true;
        } catch (WebDriverException e) {
            return false;
        }
    }

    /**
     * Detects a dead server-side session by checking whether the login form is showing where an
     * app page was expected. Must be called with {@link #isBrowserAlive()} already confirmed true —
     * a dead driver throws here too, but this method reports that as "not alive" rather than crashing.
     */
    public static boolean isSessionAlive() {
        if (!isBrowserAlive()) return false;
        try {
            return DriverManager.getDriver().findElements(LOGIN_USERNAME_FIELD).isEmpty();
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** Best-effort — true unless a loading mask is genuinely visible right now. */
    public static boolean isLoaderVisible() {
        return WaitUtils.isExtMaskPresent(DriverManager.getDriver());
    }

    /**
     * True if a blocking dialog (server error, delete confirm, message box) is currently on
     * screen. Reuses the same ExtJS message-box/window pattern already used across the page
     * objects (e.g. AccountGroupListPage's SERVER_ERROR_OK/DELETE_CONFIRM_YES).
     */
    public static boolean isPopupPresent() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) return false;
        try {
            return !driver.findElements(By.xpath(
                    "//div[contains(@class,'x-window') or contains(@class,'x-message-box')]" +
                    "[not(contains(@class,'x-hide-display'))]")).isEmpty();
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** Composite check used before attempting Recovery Levels 1-3 (no re-login needed). */
    public static boolean canRecoverWithoutLogin() {
        return isBrowserAlive() && isSessionAlive();
    }
}
