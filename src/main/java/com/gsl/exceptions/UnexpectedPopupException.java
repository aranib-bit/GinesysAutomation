package com.gsl.exceptions;

import com.gsl.popup.PopupResult;

/**
 * Thrown when an unexpected application popup appears during a positive test flow.
 * The message is pre-formatted with full business context so the failure reason
 * is immediately visible in the test report without further investigation.
 */
public final class UnexpectedPopupException extends RuntimeException {

    public UnexpectedPopupException(PopupResult popup, String actionName) {
        super(buildMessage(popup, actionName));
    }

    private static String buildMessage(PopupResult popup, String actionName) {
        return String.format("""
                Unexpected Application Popup Detected

                Popup Type:      %s
                Popup Header:    %s
                Popup Message:   %s
                Triggered After: %s
                Current URL:     %s
                Detected At:     %s
                """,
                popup.popupType(),
                popup.header(),
                popup.message(),
                actionName,
                popup.currentUrl(),
                popup.detectedTime());
    }
}
