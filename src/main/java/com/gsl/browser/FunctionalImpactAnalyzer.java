package com.gsl.browser;

import java.util.List;

public final class FunctionalImpactAnalyzer {

    private FunctionalImpactAnalyzer() {}

    public static boolean hasCriticalImpact(List<BrowserLogRecord> logs) {
        return logs.stream().anyMatch(r -> r.level() == LogSeverity.CRITICAL);
    }

    public static boolean hasWarningImpact(List<BrowserLogRecord> logs) {
        return logs.stream().anyMatch(r -> r.level() == LogSeverity.WARNING);
    }

    public static List<BrowserLogRecord> getCritical(List<BrowserLogRecord> logs) {
        return logs.stream().filter(r -> r.level() == LogSeverity.CRITICAL).toList();
    }

    public static List<BrowserLogRecord> getWarnings(List<BrowserLogRecord> logs) {
        return logs.stream().filter(r -> r.level() == LogSeverity.WARNING).toList();
    }
}
