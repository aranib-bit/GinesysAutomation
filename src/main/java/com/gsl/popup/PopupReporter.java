package com.gsl.popup;

import com.gsl.reports.ExtentLogger;

/**
 * Writes a structured popup diagnostics block into the ExtentReport.
 * Called BEFORE throwing UnexpectedPopupException so the report captures
 * full context including the pre-dismissal screenshot.
 */
public final class PopupReporter {

    private PopupReporter() {}

    public static void reportUnexpectedPopup(PopupResult popup, String actionName) {
        // P7: Prominent failure marker FIRST so it's visible in the test summary panel
        String failMsg = "⚠ Unexpected Popup on [" + actionName + "] — "
                + popup.popupType() + " | Header: " + popup.header();
        if (popup.screenshotBase64() != null) {
            ExtentLogger.failWithBase64Screenshot(failMsg, popup.screenshotBase64());
        } else {
            ExtentLogger.fail(failMsg);
        }

        // P7: Diagnostics table — richer fields including Page Title (moved Triggered After first)
        String[][] table = {
                {"Field",            "Value"},
                {"Triggered After",  actionName},
                {"Popup Type",       popup.popupType().name()},
                {"Header",           popup.header()},
                {"Message",          popup.message()},
                {"Page Title",       popup.pageTitle()},
                {"URL",              popup.currentUrl()},
                {"Detected At",      popup.detectedTime().toString()}
        };
        ExtentLogger.infoTable(table);
    }
}
