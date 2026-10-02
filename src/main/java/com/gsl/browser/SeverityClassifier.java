package com.gsl.browser;

/**
 * Classifies a raw log message string into a LogSeverity level.
 * Evaluation order guarantees IGNORE is checked first so noisy patterns never reach CRITICAL/WARNING.
 */
public final class SeverityClassifier {

    private SeverityClassifier() {}

    /** {@link #classify(String, String)} with the raw JS level defaulted to {@code "log"}. */
    public static LogSeverity classify(String message) {
        return classify(message, "log");
    }

    /**
     * Classifies using both the message text and the raw JS log level.
     * console.error / console.warn that don't match any pattern are promoted to WARNING
     * so ExtJS-specific error messages don't get buried as INFO.
     */
    public static LogSeverity classify(String message, String rawJsLevel) {
        if (message == null || message.isBlank()) return LogSeverity.IGNORE;
        if (PatternMatcherEngine.isIgnored(message))  return LogSeverity.IGNORE;
        if (PatternMatcherEngine.isCritical(message)) return LogSeverity.CRITICAL;
        if (PatternMatcherEngine.isWarning(message))  return LogSeverity.WARNING;
        if ("error".equals(rawJsLevel) || "warn".equals(rawJsLevel)) return LogSeverity.WARNING;
        return LogSeverity.INFO;
    }
}
