package com.gsl.reports;

/**
 * Named facade over {@link ReportLogger#business}/{@link ReportLogger#validation} — business
 * logic only. Session/Navigation/Recovery now log to their own categories (see
 * {@link SessionLogger}/{@link NavigationLogger}/{@link RecoveryLogger}), so a test's own Test
 * Report node stays free of framework noise.
 */
public final class TestLogger {

    private TestLogger() {}

    /** Logs a business-step line under the test's own report node. */
    public static void step(String message, Object... args) {
        ReportLogger.business(message, args);
    }

    /** Logs a validation-outcome line under the test's own report node. */
    public static void validated(String message, Object... args) {
        ReportLogger.validation(message, args);
    }
}
