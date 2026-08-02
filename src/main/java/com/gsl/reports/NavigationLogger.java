package com.gsl.reports;

/** Named facade over {@link ReportLogger}'s {@code navigation*} shorthands — the Navigation Report. */
public final class NavigationLogger {

    private NavigationLogger() {}

    public static void navigated(String checkpoint, long millis) {
        ReportLogger.navigation("{} — {}ms", checkpoint, millis);
        PerformanceSummaryStats.recordNavigationPerformed();
    }

    public static void alreadyLoaded(String checkpoint) {
        ReportLogger.navigation("{} — Already Loaded (Navigation Skipped)", checkpoint);
        PerformanceSummaryStats.recordNavigationSkipped();
    }

    public static void warn(String message, Object... args) {
        ReportLogger.navigationWarn(message, args);
    }
}
