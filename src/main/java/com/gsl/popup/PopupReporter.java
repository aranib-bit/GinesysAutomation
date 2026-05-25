package com.gsl.popup;

import com.gsl.reports.ExtentLogger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

        // Diagnostics table — Message excluded (rendered separately below for readability)
        String[][] table = {
                {"Field",            "Value"},
                {"Triggered After",  actionName},
                {"Popup Type",       popup.popupType().name()},
                {"Header",           popup.header()},
                {"Page Title",       popup.pageTitle()},
                {"URL",              popup.currentUrl()},
                {"Detected At",      popup.detectedTime().toString()}
        };
        ExtentLogger.infoTable(table);

        // Full error message rendered as HTML in a scrollable block + download link
        String downloadLink = buildDownloadLink(popup, actionName);
        String msgHtml = downloadLink
                + "<div style='max-height:300px;overflow-y:auto;background:#fff8f8;"
                + "border:1px solid #e88;padding:8px;font-size:11px;color:#333;word-break:break-all;margin-top:6px'>"
                + popup.message()
                + "</div>";
        ExtentLogger.infoHtmlOnChildNode("Full Error Message", msgHtml);
    }

    public static void reportHandledPopup(PopupResult popup, String actionName) {
        String[][] table = {
                {"Field",            "Value"},
                {"Triggered After",  actionName},
                {"Popup Type",       popup.popupType().name()},
                {"Header",           popup.header()},
                {"Page Title",       popup.pageTitle()},
                {"URL",              popup.currentUrl()},
                {"Detected At",      popup.detectedTime().toString()}
        };
        ExtentLogger.infoTable(table);

        String downloadLink = buildDownloadLink(popup, actionName);
        String msgHtml = downloadLink
                + "<div style='max-height:300px;overflow-y:auto;background:#fffbe6;"
                + "border:1px solid #e6a817;padding:8px;font-size:11px;color:#333;word-break:break-all;margin-top:6px'>"
                + popup.message()
                + "</div>";
        ExtentLogger.infoHtmlOnChildNode("Handled Popup — Full Message", msgHtml);
    }

    private static String buildDownloadLink(PopupResult popup, String actionName) {
        String plainText = "Header        : " + popup.header() + "\n"
                + "Type          : " + popup.popupType().name() + "\n"
                + "Triggered After: " + actionName + "\n"
                + "Page Title    : " + popup.pageTitle() + "\n"
                + "URL           : " + popup.currentUrl() + "\n"
                + "Detected At   : " + popup.detectedTime() + "\n"
                + "\n--- Full Error Message ---\n"
                + toPlainText(popup.message());

        String base64 = Base64.getEncoder()
                .encodeToString(plainText.getBytes(StandardCharsets.UTF_8));

        // Safe filename: replace characters not allowed in filenames
        String filename = "error_popup_" + popup.header().replaceAll("[^a-zA-Z0-9_-]", "_")
                + "_" + popup.detectedTime().toString().replaceAll("[:.T]", "-") + ".txt";

        return "<a href='data:text/plain;base64," + base64 + "' download='" + filename + "' "
                + "style='display:inline-block;padding:4px 10px;background:#c0392b;color:#fff;"
                + "border-radius:3px;font-size:11px;text-decoration:none;font-weight:bold'>"
                + "&#11015; Download Error Log</a>";
    }

    private static String toPlainText(String innerHTML) {
        if (innerHTML == null) return "";
        return innerHTML
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .trim();
    }
}
