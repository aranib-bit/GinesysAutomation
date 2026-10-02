package com.gsl.popup;

import com.gsl.enums.PopupType;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of an application popup captured at the moment of detection by
 * {@link PopupHandler#detectPopup()}, carried into classification/reaction ({@link PopupMonitor})
 * and reporting ({@link PopupReporter}).
 *
 * @param header         the messagebox's title text (e.g. "Success", "Error", "Adjustment Failed")
 * @param message        the messagebox's body content, captured as inner HTML (not plain text)
 * @param popupType      the classification derived from {@code header}
 * @param currentUrl     the browser URL at the moment of detection
 * @param pageTitle      the browser page title at the moment of detection
 * @param detectedTime   when this snapshot was captured
 * @param screenshotPath path to the screenshot taken before dismissal; {@code null} if the driver
 *                       was unresponsive at capture time
 */
public record PopupResult(
        String        header,
        String        message,
        PopupType     popupType,
        String        currentUrl,
        String        pageTitle,           // P7: browser page title at time of detection
        LocalDateTime detectedTime,
        String        screenshotPath
) {}
