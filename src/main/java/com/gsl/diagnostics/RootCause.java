package com.gsl.diagnostics;

import com.gsl.enums.ReportSeverity;

/**
 * Immutable verdict emitted by {@link RootCauseClassifier} for a failed test.
 *
 * @param category   stable classification key (see {@link RootCauseCategory})
 * @param detail     human-readable explanation, surfaced in the failure summary banner
 * @param severity   report severity used for color-coding
 * @param confidence "HIGH" | "MEDIUM" | "LOW" — borrowed from the JS classifier when applicable
 */
public record RootCause(RootCauseCategory category, String detail,
                        ReportSeverity severity, String confidence) {

    /** Convenience factory using the category's default severity. */
    public static RootCause of(RootCauseCategory category, String detail, String confidence) {
        return new RootCause(category, detail, category.defaultSeverity(), confidence);
    }
}
