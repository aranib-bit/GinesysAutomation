package com.gsl.browser;

/**
 * Classifies a raw log message string into a LogSeverity level.
 * Evaluation order guarantees IGNORE is checked first so noisy patterns never reach CRITICAL/WARNING.
 */
public final class SeverityClassifier {

    private SeverityClassifier() {}

    public static LogSeverity classify(String message) {
        if (message == null || message.isBlank()) return LogSeverity.IGNORE;
        if (PatternMatcherEngine.isIgnored(message))  return LogSeverity.IGNORE;
        if (PatternMatcherEngine.isCritical(message)) return LogSeverity.CRITICAL;
        if (PatternMatcherEngine.isWarning(message))  return LogSeverity.WARNING;
        return LogSeverity.INFO;
    }
}
