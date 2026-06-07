package com.gsl.enums;

/**
 * Severity level for a report log entry.
 *
 * MEDIUM is the implicit default and renders no badge — it represents normal
 * informational steps and does not add visual noise. Only levels that deviate
 * from the expected (CRITICAL, HIGH, LOW, TRACE) are rendered as colored badges.
 */
public enum ReportSeverity {
    CRITICAL,  // App crash, data corruption, test-blocking assertion
    HIGH,      // Business-rule failure, unexpected state
    MEDIUM,    // Normal informational step (default — badge suppressed)
    LOW,       // Debug/verbose info
    TRACE      // Fine-grained internal detail
}
