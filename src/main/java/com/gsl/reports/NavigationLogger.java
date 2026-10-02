package com.gsl.reports;

/** Named facade over {@link ReportLogger}'s {@code navigation*} shorthands — the Navigation Report. */
public final class NavigationLogger {

    private NavigationLogger() {}

    /** Logs that {@code checkpoint} was navigated to and took {@code millis}, and tallies it in {@link PerformanceSummaryStats}. */
    public static void navigated(String checkpoint, long millis) {
        ReportLogger.navigation("{} — {}ms", checkpoint, millis);
        PerformanceSummaryStats.recordNavigationPerformed();
    }

    /** Logs that {@code checkpoint} was already loaded and navigation was skipped, and tallies it in {@link PerformanceSummaryStats}. */
    public static void alreadyLoaded(String checkpoint) {
        ReportLogger.navigation("{} — Already Loaded (Navigation Skipped)", checkpoint);
        PerformanceSummaryStats.recordNavigationSkipped();
    }

    /** Logs a navigation-category warning line. */
    public static void warn(String message, Object... args) {
        ReportLogger.navigationWarn(message, args);
    }
}
