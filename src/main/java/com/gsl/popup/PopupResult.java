package com.gsl.popup;

import com.gsl.enums.PopupType;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of an application popup captured at the moment of detection.
 * screenshotBase64 may be null if the driver was unresponsive at capture time.
 */
public record PopupResult(
        String        header,
        String        message,
        PopupType     popupType,
        String        currentUrl,
        String        pageTitle,           // P7: browser page title at time of detection
        LocalDateTime detectedTime,
        String        screenshotBase64
) {}
