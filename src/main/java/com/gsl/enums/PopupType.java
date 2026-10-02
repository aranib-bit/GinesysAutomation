package com.gsl.enums;

/**
 * Classification of an ExtJS popup/dialog, derived from its header text by {@link #fromHeader}
 * and used by {@code PopupMonitor}/{@code PopupHandler} to decide how a popup encountered during
 * a test should be handled (e.g. dismissed vs. treated as a failure signal).
 */
public enum PopupType {
    ERROR, WARNING, INFO, SUCCESS, CONFIRM,
    /** Access-denied/unauthorized dialog. */
    SECURITY,
    /** Session-expired dialog. */
    SESSION_TIMEOUT,
    /** Header text didn't match any known keyword. */
    UNKNOWN;

    /**
     * Classifies {@code header} by keyword match (case-insensitive substring), checked in
     * declaration order below; returns {@link #UNKNOWN} for a blank header or no match.
     */
    public static PopupType fromHeader(String header) {
        if (header == null || header.isBlank()) return UNKNOWN;
        String h = header.toUpperCase();
        if (h.contains("ERROR"))                              return ERROR;
        if (h.contains("WARNING"))                            return WARNING;
        if (h.contains("INFO"))                               return INFO;
        if (h.contains("SUCCESS"))                            return SUCCESS;
        if (h.contains("CONFIRM"))                            return CONFIRM;
        if (h.contains("ACCESS DENIED") || h.contains("UNAUTHORIZED")) return SECURITY;
        if (h.contains("SESSION")       || h.contains("EXPIRED"))      return SESSION_TIMEOUT;
        return UNKNOWN;
    }
}
