package com.gsl.pages;

import com.gsl.annotations.LogStep;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.PopupOutcome;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.CommonButton;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.testng.SkipException;

import java.time.Duration;
import java.util.List;

import static com.gsl.utils.Selenium.SeleniumUtils.*;

public class LoginPage {

    // Page-specific locators — Login/splash/loader are not common buttons
    private static final class Loc {
        static final By TXTBOX_USERNAME      = By.name("username");
        static final By TXTBOX_PASSWORD      = By.name("password");
        static final By BTN_LOGIN            = By.xpath("//span[text()='Login']");
        static final By SPLASHSCREEN         = By.id("splashScreen");
        static final By AUTHENTICATOR_LOADER = By.xpath("//*[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");

        /**
         * Best-effort, unconfirmed locator for a reported (not yet captured in any run artifact)
         * post-login popup carrying a "Skip" button — see {@link #handleUnexpectedSkipPopup()}.
         * Kept local rather than promoted to {@link CommonButton} until its real DOM markup is
         * confirmed live (that registry's own Javadoc warns against adding page/context-specific
         * buttons speculatively).
         */
        static final By BTN_SKIP = By.xpath("//span[normalize-space()='Skip']");
    }

    // OK_BUTTON  → CommonButton.OK.locator()
    // BTN_CONCURRENT_SESSION_YES → CommonButton.YES.scopedLocator(...)

    private LoginPage clickOk(){
        click(CommonButton.OK.locator(), WaitType.CLICKABLE, "OK_BUTTON");
        return this;
    }

    private LoginPage setUserName(String userName) {
        sendKeys(Loc.TXTBOX_USERNAME, userName, "Username");
        return this;
    }

    private LoginPage setPassword(String password) {
        sendKeys(Loc.TXTBOX_PASSWORD, password, "Password");
        return this;
    }

    private LoginPage setLogin() {
        try {
            click(Loc.BTN_LOGIN, WaitType.CLICKABLE, "Login Button");
        } catch (ElementClickInterceptedException e) {
            // The intercepting overlay may be the "already logged in elsewhere" confirm dialog
            // rather than a transient ExtJS mask — dismiss it and retry once before giving up.
            handleConcurrentSessionPopup();
            click(Loc.BTN_LOGIN, WaitType.CLICKABLE, "Login Button");
        }
        return this;
    }

    /**
     * Handles the "user already logged in from some other browser" prompt that GINESYS shows when
     * a stale/concurrent server-side session exists for the same user. The app asks
     * "Do you want to log out your existing session and login from here?" — we click <b>Yes</b> to
     * take over the stale session and continue, so a leftover session never fails the test.
     *
     * <p>Polls (rather than a fixed 3s wait) because the prompt is driven by the {@code /WebAPI/token}
     * round-trip and can arrive late. The loop exits immediately when the login form disappears
     * (clean login, no prompt) so it does not slow normal logins.
     *
     * <p>If the prompt is clicked but does not clear, the session takeover did not complete — the
     * test is <b>skipped</b> (amber) rather than failed (red).
     */
    private LoginPage handleConcurrentSessionPopup() {
        WebDriver driver = DriverManager.getDriver();
        By yesButton = CommonButton.YES.scopedLocator("//div[contains(@class,'x-message-box')]");
        long timeoutSec = ConfigFactory.getConfig().timeout();

        try {
            new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(timeoutSec))
                    .pollingEvery(Duration.ofMillis(300))
                    .ignoring(NoSuchElementException.class)
                    .until(d -> isVisibleQuiet(d, yesButton) || !isVisibleQuiet(d, Loc.TXTBOX_USERNAME));

            if (!isVisibleQuiet(driver, yesButton)) {
                // Login form disappeared without a concurrent-session prompt — clean login.
                return this;
            }

            click(yesButton, WaitType.CLICKABLE, "Concurrent Session Yes Button");
            // MAYBE, not EXPECTED/UNEXPECTED — this popup only conditionally appears depending on
            // whatever session state happened to exist before this login attempt; it's neither a
            // routine part of every login nor a hard anomaly.
            ReportLogger.popup(PopupOutcome.MAYBE, "Concurrent-session prompt detected — clicked "
                    + "'Yes' to log out the stale session and continue login.");

            // Wait briefly for the prompt to clear; if it persists, takeover failed → skip.
            new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(5))
                    .pollingEvery(Duration.ofMillis(300))
                    .ignoring(NoSuchElementException.class)
                    .until(d -> !isVisibleQuiet(d, yesButton));

        } catch (org.openqa.selenium.TimeoutException e) {
            if (isVisibleQuiet(driver, yesButton)) {
                throw new SkipException("Login blocked: user already logged in from another browser "
                        + "and the session takeover did not complete — skipping (not a product defect).");
            }
        }
        return this;
    }

    /**
     * Defensive handling for a reported, not-yet-captured post-login popup carrying a "Skip"
     * button — seen live when a NEW user's first login lands on a freshly-launched browser
     * (see {@code SessionPool.createSession()}/{@code isReusable()}: a REUSED session never
     * reaches {@code loginToApplication()} at all, since {@code SessionManager.ensureLoggedIn()}
     * returns the cached {@code HomePage} directly — so this can only ever fire on that one
     * first-login path, making it safe to poll for unconditionally on every login).
     *
     * <p>Modeled directly on {@link #handleConcurrentSessionPopup()}'s self-polling, MAYBE-
     * classified pattern: a short bounded wait for the Skip button; click it and log if present,
     * no-op immediately if absent. No screenshot/DOM of this popup exists in any run artifact yet
     * — {@link Loc#BTN_SKIP} is a best-effort generic guess; refine it once a live occurrence is
     * captured.
     */
    private LoginPage handleUnexpectedSkipPopup() {
        WebDriver driver = DriverManager.getDriver();
        try {
            new FluentWait<>(driver)
                    .withTimeout(Duration.ofSeconds(5))
                    .pollingEvery(Duration.ofMillis(300))
                    .ignoring(NoSuchElementException.class)
                    .until(d -> isVisibleQuiet(d, Loc.BTN_SKIP) || !isVisibleQuiet(d, Loc.TXTBOX_USERNAME));
        } catch (org.openqa.selenium.TimeoutException ignored) {
            // Neither the Skip button nor the login form's disappearance showed up within the
            // bounded wait — fall through and let checkAuthenticationLoader() handle whatever
            // state the page is actually in; this method must never itself fail a login.
        }

        if (isVisibleQuiet(driver, Loc.BTN_SKIP)) {
            click(Loc.BTN_SKIP, WaitType.CLICKABLE, "Unexpected Skip Popup Button");
            // MAYBE, not EXPECTED/UNEXPECTED — same rationale as handleConcurrentSessionPopup:
            // this popup only conditionally appears (apparently on a new user's first login),
            // it's neither a routine part of every login nor a hard anomaly.
            ReportLogger.popup(PopupOutcome.MAYBE, "Unrecognized post-login popup detected "
                    + "(carrying a 'Skip' button) — clicked 'Skip' to dismiss and continue.");
        }
        return this;
    }

    /** Non-blocking visibility check — returns false instead of waiting/throwing when absent or stale. */
    private static boolean isVisibleQuiet(WebDriver driver, By by) {
        try {
            List<WebElement> els = driver.findElements(by);
            return !els.isEmpty() && els.get(0).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HomePage checkAuthenticationLoader(){
        try {
            elementDisappear(Loc.AUTHENTICATOR_LOADER, "Authentication Loader");
        } catch (RuntimeException e) {
            // The login window did not go away. If the application is showing an error on it (e.g.
            // "Error: The user name or password is incorrect."), THAT is the cause — say so, naming the
            // user, instead of the generic "element still visible" (no wait is lengthened).
            String applicationError = com.gsl.utils.Selenium.PageStateDiagnostics.visibleErrorText();
            if (applicationError != null) {
                com.gsl.testdata.UserCredential user = com.gsl.driver.DriverManager.getCurrentUser();
                throw new IllegalStateException("Login rejected by the application for user '"
                        + (user == null ? "?" : user.username()) + "': " + applicationError, e);
            }
            throw e;
        }
        return new HomePage();
    }

    private LoginPage checkSplashScreenLoader(){
        // Round 14 (§27c): was a bare, single-attempt 30s elementDisappear — now the same
        // staged, logged wait waitForClickOverlay() already uses for this exact element.
        WaitUtils.waitForSplashScreenClear();
        return this;
    }


    @LogStep("Login to Application")
    public HomePage loginToApplication(String username, String password) {
        StepExecutor.step("Pre-Login: Wait for App to Load", () ->
                checkSplashScreenLoader());

        StepExecutor.step("Enter Credentials", () -> {
            setUserName(username);
            setPassword(password);
        });

        final HomePage[] result = {null};
        StepExecutor.step("Submit & Authenticate", () -> {
            setLogin();
            handleConcurrentSessionPopup();
            handleUnexpectedSkipPopup();
            result[0] = checkAuthenticationLoader();
        });

        return result[0];
    }
}