package com.gsl.popup;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.PopupType;
import com.gsl.exceptions.UnexpectedPopupException;
import com.gsl.reports.ExtentLogger;

/**
 * Single entry point for unexpected popup detection.
 * Called automatically by SeleniumUtils.click() after every click action.
 * SUCCESS and INFO popups are ignored — handled by DocumentMessageBox as before.
 */
public final class PopupMonitor {

    private PopupMonitor() {}

    /**
     * Probes for an unexpected popup after a business action.
     * Silently returns if no popup is detected or if detection is disabled via config.
     *
     * @param actionName human-readable description of the preceding action (used in failure message)
     * @throws UnexpectedPopupException if an unexpected popup is detected
     */
    public static void checkAfterAction(String actionName) {
        if (!ConfigFactory.getWaitConfig().popupDetectionEnabled()) return;

        PopupResult popup = PopupHandler.detectPopup();
        if (popup == null) return;

        // SUCCESS and INFO are expected responses handled by DocumentMessageBox — ignore them
        if (popup.popupType() == PopupType.SUCCESS || popup.popupType() == PopupType.INFO) return;

        // CONFIRM dialogs are expected interaction flows (e.g. concurrent session, discard changes)
        // Not dismissed — page object handles the Yes/No. Just note it and continue.
        if (popup.popupType() == PopupType.CONFIRM) {
            ExtentLogger.info("ℹ Confirm dialog detected after [" + actionName + "] — \""
                    + popup.header() + "\" — handled by page flow");
            return;
        }

        // WARNING is logged with full details but does not fail the test
        if (popup.popupType() == PopupType.WARNING) {
            ExtentLogger.warning("⚠ Warning popup after [" + actionName + "] — " + popup.header());
            PopupReporter.reportHandledPopup(popup, actionName);
            return;
        }

        PopupReporter.reportUnexpectedPopup(popup, actionName);
        throw new UnexpectedPopupException(popup, actionName);
    }

    /**
     * Same as checkAfterAction but for handled scenarios — logs the popup as a warning
     * and returns without throwing. Use when the caller knows a popup may appear and
     * deals with it, but still wants it noted in the report.
     */
    public static void noteAfterAction(String actionName) {
        if (!ConfigFactory.getWaitConfig().popupDetectionEnabled()) return;

        PopupResult popup = PopupHandler.detectPopup();
        if (popup == null) return;

        if (popup.popupType() == PopupType.SUCCESS || popup.popupType() == PopupType.INFO) return;

        ExtentLogger.warning("⚠ Handled popup on [" + actionName + "] — "
                + popup.popupType() + " | Header: " + popup.header());
        PopupReporter.reportHandledPopup(popup, actionName);
    }
}
