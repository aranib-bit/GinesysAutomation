package com.gsl.driver;

import com.gsl.utils.Log;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.UnreachableBrowserException;

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

    /**
     * Cheapest possible probe — a dead/closed driver throws on any call, including getTitle().
     *
     * <p><b>Round 18</b> — retries specifically on {@link UnreachableBrowserException}, up to 3
     * total attempts, 500ms apart. Confirmed live (multiple runs, e.g. 20260926_180427) that this
     * exact exception fires when 2-3 concurrent Chrome sessions' chromedriver HTTP calls transiently
     * time out under momentary contention — with NO corresponding Windows "Application Error" crash
     * record or Crashpad minidump at the same moment, i.e. Chrome itself was still alive, just
     * briefly unresponsive. A single failed probe used to be treated as conclusive "dead"
     * ({@link com.gsl.driver.FailureClassifier} escalates straight to Level-4 session teardown),
     * destroying a perfectly healthy session — and that teardown/recreate itself adds MORE load
     * right when the system was already strained. Every OTHER {@link WebDriverException} subtype
     * (closed window, invalid session, etc.) keeps the original immediate-{@code false} behavior —
     * those are more clearly conclusive, so retrying them would only waste time.
     */
    public static boolean isBrowserAlive() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) return false;

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                driver.getTitle();
                return true;
            } catch (UnreachableBrowserException e) {
                if (attempt == maxAttempts) return false;
                Log.warn("[HealthChecker] isBrowserAlive() attempt {}/{} hit UnreachableBrowserException "
                        + "— retrying after a short settle delay (transient communication timeout, not "
                        + "necessarily a real crash).", attempt, maxAttempts);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } catch (WebDriverException e) {
                return false;
            }
        }
        return false;
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

    /**
     * True if a <b>top-level modal window layer</b> — an Add/Modify form, a picker, a message box —
     * is currently rendered and visible. This is the "is the page dirty" probe used before a session
     * is handed to the next test; {@link #isPopupPresent()} is deliberately not reused for that
     * purpose because its {@code contains(@class,'x-window')} substring match also hits
     * {@code x-window-body}, {@code x-window-bodyWrap}, {@code x-window-item} and
     * {@code x-window-text}, all of which exist on a perfectly clean page.
     *
     * <p>Three conditions, all required:
     * <ul>
     *   <li>{@code x-window} as a whole class <i>token</i>, not a substring — hence a CSS class
     *       selector ({@code div.x-window}), which is token-exact by definition, rather than the
     *       XPath {@code contains()} the probe above uses.</li>
     *   <li>{@code aria-hidden} is not {@code "true"} — ExtJS keeps closed windows in the DOM.</li>
     *   <li>A non-zero rendered box ({@code offsetParent}/{@code getClientRects}) — the same
     *       "actually visible, not a zero-size leftover" test {@code WaitUtils.waitForClickOverlay}
     *       and {@code waitForExtMaskBestEffort} already apply to masks.</li>
     * </ul>
     *
     * <p>Confirmed against the DOM captured in run 20260807_095328, where the leftover
     * {@code Add: General Voucher} form rendered as
     * {@code <div class="x-window x-layer x-window-gsl_Type1 …" role="dialog" aria-hidden="false">}.
     *
     * <p>Never throws — a dead driver reports "no modal" and lets the browser/session probes above
     * classify the real problem.
     */
    public static boolean isModalWindowOpen() {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) return false;
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "var ws = document.querySelectorAll('div.x-window');" +
                    "for (var i = 0; i < ws.length; i++) {" +
                    "  var w = ws[i];" +
                    "  if (w.getAttribute('aria-hidden') === 'true') continue;" +
                    "  if (w.offsetParent === null && w.getClientRects().length === 0) continue;" +
                    "  var r = w.getBoundingClientRect();" +
                    "  if (r.width > 0 && r.height > 0) return true;" +
                    "} return false;");
            return Boolean.TRUE.equals(result);
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** Composite check used before attempting Recovery Levels 1-3 (no re-login needed). */
    public static boolean canRecoverWithoutLogin() {
        return isBrowserAlive() && isSessionAlive();
    }
}
