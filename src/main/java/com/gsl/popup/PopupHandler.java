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
 * Low-level popup detection engine.
 * Checks whether the application's ExtJS messagebox is currently visible,
 * captures its content, and returns a typed PopupResult.
 * Does NOT contain any business logic — classification is delegated to PopupType.
 */
public final class PopupHandler {

    private static final Logger LOG = LogManager.getLogger(PopupHandler.class);

    private PopupHandler() {}

    /**
     * Probes for a visible ExtJS messagebox.
     *
     * @return PopupResult if a messagebox is visible; null if no popup within the configured timeout.
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

    private static void dismissPopup() {
        try {
            WebElement btn = DriverManager.getDriver()
                    .findElement(AppPopupLocators.MESSAGEBOX_DISMISS_BTN);
            btn.click();
        } catch (Exception ignored) {}
    }

    private static String safeText(org.openqa.selenium.By locator) {
        try {
            return DriverManager.getDriver().findElement(locator).getText();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean hasMultipleActionButtons() {
        return DriverManager.getDriver()
                .findElements(AppPopupLocators.MESSAGEBOX_ACTION_BTNS)
                .size() >= 2;
    }

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

    private static String safeUrl() {
        try {
            return DriverManager.getDriver().getCurrentUrl();
        } catch (Exception e) {
            return "";
        }
    }

    // P7: Capture the browser page title for richer popup diagnostics
    private static String safeTitle() {
        try {
            return DriverManager.getDriver().getTitle();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeScreenshot() {
        try {
            return ScreenshotUtils.saveScreenshot("popup");
        } catch (Exception e) {
            return null;
        }
    }
}
