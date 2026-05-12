package com.gsl.enums;

public enum PopupType {
    ERROR, WARNING, INFO, SUCCESS, SECURITY, SESSION_TIMEOUT, UNKNOWN;

    public static PopupType fromHeader(String header) {
        if (header == null || header.isBlank()) return UNKNOWN;
        String h = header.toUpperCase();
        if (h.contains("ERROR"))                              return ERROR;
        if (h.contains("WARNING"))                            return WARNING;
        if (h.contains("INFO"))                               return INFO;
        if (h.contains("SUCCESS"))                            return SUCCESS;
        if (h.contains("ACCESS DENIED") || h.contains("UNAUTHORIZED")) return SECURITY;
        if (h.contains("SESSION")       || h.contains("EXPIRED"))      return SESSION_TIMEOUT;
        return UNKNOWN;
    }
}
