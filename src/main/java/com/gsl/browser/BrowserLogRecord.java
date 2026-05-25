package com.gsl.browser;

public record BrowserLogRecord(
        String      message,
        LogSeverity level,
        long        timestamp,
        String      source,
        String      stack,
        String      url,
        int         lineNumber,             // from window.onerror 'line' arg; 0 for CONSOLE entries
        int         columnNumber,           // from window.onerror 'col' arg; 0 for CONSOLE entries
        String      capturedAtStep,         // test step active when error fired; "unknown" if outside a step
        int         capturedAfterActionMs,  // ms between step start and error fire; -1 if unknown
        String      pageReadyState          // document.readyState at error time: loading|interactive|complete
) {
    /** Convenience factory for CONSOLE entries — timing fields are not applicable. */
    public static BrowserLogRecord console(
            String message, LogSeverity level, long timestamp, String source) {
        return new BrowserLogRecord(message, level, timestamp, source, "", "", 0, 0, "n/a", -1, "n/a");
    }

    /** Convenience factory for JS_ERROR entries with full timing context. */
    public static BrowserLogRecord jsError(
            String message, long timestamp, String url, String stack,
            int lineNumber, int columnNumber,
            String capturedAtStep, int capturedAfterActionMs, String pageReadyState) {
        return new BrowserLogRecord(message, LogSeverity.CRITICAL, timestamp,
                "onerror", stack, url, lineNumber, columnNumber,
                capturedAtStep, capturedAfterActionMs, pageReadyState);
    }
}
