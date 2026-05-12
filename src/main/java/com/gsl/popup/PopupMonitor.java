package com.gsl.popup;

import com.gsl.config.ConfigFactory;
import com.gsl.exceptions.UnexpectedPopupException;

/**
 * Single entry point for unexpected popup detection.
 *
 * Usage in page objects (for business-triggering actions only):
 *   SeleniumUtils.clickAndCheckPopup(SAVE_BTN, "Click Save Button");
 *   // or manually:
 *   PopupMonitor.checkAfterAction("Click Save Button");
 *
 * For negative test cases where a popup IS expected, keep using
 * the explicit DocumentPopup / DocumentMessageBox handling as before.
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
        if (!ConfigFactory.getConfig().popupDetectionEnabled()) return;

        PopupResult popup = PopupHandler.detectPopup();
        if (popup == null) return;

        PopupReporter.reportUnexpectedPopup(popup, actionName);
        throw new UnexpectedPopupException(popup, actionName);
    }
}
