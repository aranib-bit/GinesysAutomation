package com.gsl.browser;

/** Severity a browser log message is classified into by {@link SeverityClassifier}; {@code IGNORE} entries are dropped entirely by {@link AdvancedLogFetcher}. */
public enum LogSeverity {
    CRITICAL,
    WARNING,
    INFO,
    IGNORE
}
