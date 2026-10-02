package com.gsl.popup;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.PopupType;
import com.gsl.utils.Selenium.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Low-level popup detection engine for the framework's unexpected-popup subsystem
 * ({@code com.gsl.popup}). Checks whether the application's ExtJS messagebox
 * ({@link AppPopupLocators}) is currently visible, captures its content, and returns a typed
 * {@link PopupResult}. Does NOT contain any business logic — classification is delegated to
 * {@link PopupType}, and reporting/failure decisions belong to {@link PopupMonitor}, the
 * package's actual entry point.
 */
public final class PopupHandler {

    private static final Logger LOG = LogManager.getLogger(PopupHandler.class);

    private PopupHandler() {}

    /**
     * Probes for a visible ExtJS messagebox within the configured
     * {@code popupDetectionTimeoutMs()} window. If one appears, captures its header/message/URL/
     * page title/screenshot, classifies it via {@link PopupType#fromHeader(String)} — upgraded to
     * {@link PopupType#CONFIRM} whenever 2+ action buttons are present, since a page flow must
     * choose between them regardless of header text — and dismisses it unless it is a CONFIRM
     * dialog (left for the page object to handle).
     *
     * <p>Never throws — a timeout (no popup visible) or any other detection error is swallowed and
     * reported as {@code null}, so callers can treat "no popup" and "detection failed" the same way.
     *
     * @return a {@link PopupResult} snapshot if a messagebox was visible; {@code null} otherwise
     */
    public static PopupResult detectPopup() {
        try {
            int timeoutMs = ConfigFactory.getWaitConfig().popupDetectionTimeoutMs();
            int pollMs    = ConfigFactory.getWaitConfig().popupDetectionPollingMs();
            WebElement container = new FluentWait<>(DriverManager.getDriver())
                    .withTimeout(Duration.ofMillis(timeoutMs))
                    .pollingEvery(Duration.ofMillis(pollMs))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(d -> {
                        try {
                            WebElement el = d.findElement(AppPopupLocators.MESSAGEBOX_CONTAINER);
                            return el.isDisplayed() ? el : null;
                        } catch (Exception e) {
                            return null;
                        }
                    });

            if (container == null) return null;

            String    header  = safeText(AppPopupLocators.MESSAGEBOX_HEADER);
            String    message = safeFullText(AppPopupLocators.MESSAGEBOX_BODY);
            String    url     = safeUrl();
            String    title   = safeTitle();   // P7: capture page title before dismissal
            String    shot    = safeScreenshot();
            PopupType type    = PopupType.fromHeader(header);

            // Any dialog with 2+ action buttons requires the page flow to choose (Yes/No,
            // Ignore & Proceed/Close, etc.) — treat as CONFIRM regardless of header
            if (type != PopupType.CONFIRM && hasMultipleActionButtons()) {
                type = PopupType.CONFIRM;
            }

            LOG.warn("[PopupHandler] Popup detected — type={} header='{}' message='{}' pageTitle='{}'",
                    type, header, message, title);

            // CONFIRM dialogs have Yes/No buttons — leave them for the page object to handle
            if (type != PopupType.CONFIRM) dismissPopup();

            return new PopupResult(header, message, type, url, title, LocalDateTime.now(), shot);

        } catch (TimeoutException e) {
            // Expected path — no popup visible within detection window
            return null;
        } catch (Exception e) {
            LOG.warn("[PopupHandler] Detection error (ignored): {}", e.getMessage());
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Clicks the messagebox's OK/Close button to dismiss it; failures are swallowed since dismissal is best-effort. */
    private static void dismissPopup() {
        try {
            WebElement btn = DriverManager.getDriver()
                    .findElement(AppPopupLocators.MESSAGEBOX_DISMISS_BTN);
            btn.click();
        } catch (Exception ignored) {}
    }

    /** Returns {@code locator}'s visible text, or empty string if not found. */
    private static String safeText(org.openqa.selenium.By locator) {
        try {
            return DriverManager.getDriver().findElement(locator).getText();
        } catch (Exception e) {
            return "";
        }
    }

    /** True when the messagebox shows 2+ action buttons (Yes/No, Ignore/Close, etc.) — signals a CONFIRM-style dialog regardless of header text. */
    private static boolean hasMultipleActionButtons() {
        return DriverManager.getDriver()
                .findElements(AppPopupLocators.MESSAGEBOX_ACTION_BTNS)
                .size() >= 2;
    }

    /** Returns {@code locator}'s inner HTML (not plain text) so embedded line breaks/formatting survive into the report, or empty string if not found. */
    private static String safeFullText(org.openqa.selenium.By locator) {
        try {
            WebElement el = DriverManager.getDriver().findElement(locator);
            Object result = ((JavascriptExecutor) DriverManager.getDriver())
                    .executeScript("return arguments[0].innerHTML;", el);
            return result != null ? result.toString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Returns the current browser URL, or empty string if unavailable. */
    private static String safeUrl() {
        try {
            return DriverManager.getDriver().getCurrentUrl();
        } catch (Exception e) {
            return "";
        }
    }

    /** Returns the current browser page title, or empty string if unavailable. */
    // P7: Capture the browser page title for richer popup diagnostics
    private static String safeTitle() {
        try {
            return DriverManager.getDriver().getTitle();
        } catch (Exception e) {
            return "";
        }
    }

    /** Saves a screenshot tagged "popup" and returns its path, or {@code null} if capture fails. */
    private static String safeScreenshot() {
        try {
            return ScreenshotUtils.saveScreenshot("popup");
        } catch (Exception e) {
            return null;
        }
    }
}
