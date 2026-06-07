package com.gsl.enums;

/**
 * Categorizes a report log entry by functional domain.
 * Used with {@link ReportSeverity} to produce color-coded badge headers
 * in the ExtentReport, allowing readers to filter by area without scrolling
 * through mixed framework / business / DB noise.
 */
public enum LogCategory {
    BUSINESS,    // Business flow steps (voucher creation, form submission, navigation)
    UI_ACTION,   // Selenium interactions (click, sendKeys, wait)
    VALIDATION,  // Assertions and checks (soft/hard assert, DB comparison)
    DB,          // Database queries and validation
    NETWORK,     // HTTP/API traffic
    JS,          // JavaScript console, browser errors
    PERF,        // Performance metrics and timing
    FRAMEWORK,   // Internal framework operations
    RCA          // Root cause analysis hints
}
