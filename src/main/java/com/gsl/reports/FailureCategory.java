package com.gsl.reports;

/**
 * Business-readable failure/skip bucket shown in the coverage workbook (Execution Results,
 * Failure Details, Dashboard "top failure categories"). Derived by {@link FailureClassifier}; the
 * {@link #label()} is what appears in the sheet and what the pivot/summary tables group on.
 */
public enum FailureCategory {
    NONE(""),
    UI_VALIDATION("UI validation"),
    DB_VALIDATION("DB validation"),
    SNAPSHOT_VALIDATION("Snapshot validation"),
    APP_REJECTION("Application rejected action"),
    TIMEOUT("Timeout / wait"),
    CLICK_BLOCKED("Click / interaction blocked"),
    ELEMENT_NOT_FOUND("Element not found"),
    DB_ERROR("Database error"),
    DATA_SHORTAGE("Data shortage / skipped"),
    CIRCUIT_BREAKER("Suite circuit breaker"),
    ASSERTION("Assertion mismatch"),
    OTHER("Other / unexpected");

    private final String label;

    FailureCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
