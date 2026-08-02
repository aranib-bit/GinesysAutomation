package com.gsl.reports;

/** Named facade over {@link ReportLogger#perf}, for the per-session Performance Report breakdown. */
public final class PerformanceLogger {

    private PerformanceLogger() {}

    public static void timed(String label, long millis) {
        ReportLogger.perf("{}: {}ms", label, millis);
    }
}
