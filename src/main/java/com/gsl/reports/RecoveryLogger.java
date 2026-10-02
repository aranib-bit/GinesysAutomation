package com.gsl.reports;

/** Named facade over {@link ReportLogger}'s {@code recovery*} shorthands — the Recovery Report. */
public final class RecoveryLogger {

    private RecoveryLogger() {}

    /** Logs that recovery started, with its trigger reason and escalation level. */
    public static void started(String reason, int level) {
        ReportLogger.recovery("Recovery Started — Reason: {} — Recovery Level: {}.", reason, level);
    }

    /** Logs one recovery step's outcome. */
    public static void step(String stepName, boolean passed) {
        ReportLogger.recovery("{} — {}", stepName, passed ? "PASS" : "FAIL");
    }

    /** Logs that recovery completed and execution continued, with its elapsed time. */
    public static void completed(long millis) {
        ReportLogger.recovery("Recovery Time: {}ms — Execution Continued.", millis);
    }

    /** Logs a recovery-category warning line. */
    public static void warn(String message, Object... args) {
        ReportLogger.recoveryWarn(message, args);
    }
}
