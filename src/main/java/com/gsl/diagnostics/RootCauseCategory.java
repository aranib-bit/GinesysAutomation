package com.gsl.diagnostics;

import com.gsl.enums.ReportSeverity;

/**
 * Stable, machine-readable root-cause categories produced by {@link RootCauseClassifier}.
 *
 * <p>Ordering and naming follow the <em>observed</em> failure taxonomy of this application
 * (scanned from historical ExtentReports) rather than generic Selenium theory — the most
 * frequent real failure modes (ExtJS mask intercepts, grid-row-not-found, missing test data)
 * come first. Each value carries a human display label and a default {@link ReportSeverity}
 * so the renderer can color-code consistently.
 */
public enum RootCauseCategory {

    UNEXPECTED_POPUP       ("UNEXPECTED APPLICATION POPUP",   ReportSeverity.CRITICAL),
    EXTJS_MASK_BLOCKING    ("EXTJS MASK BLOCKING",            ReportSeverity.HIGH),
    CLICK_INTERCEPTED      ("CLICK INTERCEPTED",              ReportSeverity.HIGH),
    GRID_ROW_NOT_FOUND     ("GRID ROW NOT FOUND",            ReportSeverity.HIGH),
    TEST_DATA_PREREQUISITE ("TEST DATA PREREQUISITE",        ReportSeverity.HIGH),
    APP_SAVE_REJECTED      ("APP SAVE REJECTED",             ReportSeverity.CRITICAL),
    ELEMENT_NEVER_APPEARED ("ELEMENT NEVER APPEARED",        ReportSeverity.HIGH),
    UI_TIMEOUT             ("UI TIMEOUT",                    ReportSeverity.MEDIUM),
    LOCATOR_MISMATCH       ("LOCATOR MISMATCH",              ReportSeverity.HIGH),
    DOM_REBUILT            ("DOM REBUILT DURING INTERACTION", ReportSeverity.MEDIUM),
    DB_VALIDATION_FAILURE  ("DB VALIDATION FAILURE",         ReportSeverity.CRITICAL),
    UI_CONTRACT_MISMATCH   ("UI CONTRACT MISMATCH",          ReportSeverity.MEDIUM),
    ASSERTION_FAILURE      ("ASSERTION FAILURE",             ReportSeverity.HIGH),
    JS_ERROR_BLOCKED_RENDER("JS ERROR BLOCKED RENDER",       ReportSeverity.CRITICAL),
    SERVER_ERROR           ("SERVER ERROR",                  ReportSeverity.CRITICAL),
    ENVIRONMENT            ("ENVIRONMENT / CONNECTIVITY",     ReportSeverity.HIGH),
    FRAMEWORK_NPE          ("FRAMEWORK NULL POINTER",        ReportSeverity.HIGH),
    UNKNOWN                ("UNKNOWN",                       ReportSeverity.LOW);

    private final String         label;
    private final ReportSeverity defaultSeverity;

    RootCauseCategory(String label, ReportSeverity defaultSeverity) {
        this.label           = label;
        this.defaultSeverity = defaultSeverity;
    }

    public String label() { return label; }

    public ReportSeverity defaultSeverity() { return defaultSeverity; }
}
