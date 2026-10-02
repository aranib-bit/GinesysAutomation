package com.gsl.browser;

import java.util.List;

/** Small severity-filtering helpers over a {@link BrowserLogRecord} list, shared by {@link DecisionEngine} and the Extent attachment path. */
public final class FunctionalImpactAnalyzer {

    private FunctionalImpactAnalyzer() {}

    /** @return true if any record is {@link LogSeverity#CRITICAL}. */
    public static boolean hasCriticalImpact(List<BrowserLogRecord> logs) {
        return logs.stream().anyMatch(r -> r.level() == LogSeverity.CRITICAL);
    }

    /** @return true if any record is {@link LogSeverity#WARNING}. */
    public static boolean hasWarningImpact(List<BrowserLogRecord> logs) {
        return logs.stream().anyMatch(r -> r.level() == LogSeverity.WARNING);
    }

    /** @return only the {@link LogSeverity#CRITICAL} records, in original order. */
    public static List<BrowserLogRecord> getCritical(List<BrowserLogRecord> logs) {
        return logs.stream().filter(r -> r.level() == LogSeverity.CRITICAL).toList();
    }

    /** @return only the {@link LogSeverity#WARNING} records, in original order. */
    public static List<BrowserLogRecord> getWarnings(List<BrowserLogRecord> logs) {
        return logs.stream().filter(r -> r.level() == LogSeverity.WARNING).toList();
    }
}
