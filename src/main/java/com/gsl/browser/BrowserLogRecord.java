package com.gsl.browser;

public record BrowserLogRecord(
        String      message,
        LogSeverity level,
        long        timestamp,
        String      source,
        String      stack,
        String      url
) {}
