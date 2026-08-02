package com.gsl.reports;

/** Named facade over {@link ReportLogger}'s {@code recovery*} shorthands — the Recovery Report. */
public final class RecoveryLogger {

    private RecoveryLogger() {}

    public static void started(String reason, int level) {
        ReportLogger.recovery("Recovery Started — Reason: {} — Recovery Level: {}.", reason, level);
    }

    public static void step(String stepName, boolean passed) {
        ReportLogger.recovery("{} — {}", stepName, passed ? "PASS" : "FAIL");
    }

    public static void completed(long millis) {
        ReportLogger.recovery("Recovery Time: {}ms — Execution Continued.", millis);
    }

    public static void warn(String message, Object... args) {
        ReportLogger.recoveryWarn(message, args);
    }
}
