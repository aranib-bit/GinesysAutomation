package com.gsl.reports;

/**
 * Named, discoverable facade over {@link ReportLogger}'s {@code session*} shorthands — the
 * "Logging API" the Session Report is built from. Tests/framework code should call these instead
 * of touching Extent directly, so every session-lifecycle line lands under {@code LogCategory.SESSION}
 * with consistent wording.
 */
public final class SessionLogger {

    private SessionLogger() {}

    public static void browserStarted(String browserAndVersion) {
        ReportLogger.session("Browser Started — {}", browserAndVersion);
    }

    public static void userAssigned(String username) {
        ReportLogger.session("User Assigned — {}", username);
    }

    public static void loginStarted(String username) {
        ReportLogger.session("Login Started — user '{}'.", username);
    }

    /**
     * Same real login as {@link #loginStarted}, but tagged distinctly for the one-time case: this
     * is the very first login this thread has ever performed in the suite run. Kept separate so a
     * reader can tell "unavoidable per-thread warm-up cost" apart from a normal reuse-cycle login,
     * and so {@code PoolAttributionStats} can aggregate it as Suite/Class Setup cost rather than
     * business-test time (see {@code ExecutionContext.isColdStartInProgress()}).
     */
    public static void suiteSetupLogin(String username) {
        ReportLogger.session("[SUITE/CLASS SETUP] Login Started — user '{}' (this thread's first-ever session).", username);
    }

    public static void loginSuccess(String username) {
        ReportLogger.session("Login Successful — user '{}'.", username);
    }

    public static void landingPageLoaded() {
        ReportLogger.session("Landing Page Loaded");
    }

    public static void sessionReused(String username) {
        ReportLogger.session("Reusing existing session — user '{}' — login skipped.", username);
    }

    public static void sessionTime(long millis) {
        ReportLogger.session("Session Time: {}ms", millis);
    }

    public static void sessionClosed(String reason, String username) {
        ReportLogger.session("Session closed ({}) — user '{}'.", reason, username);
    }

    public static void warn(String message, Object... args) {
        ReportLogger.sessionWarn(message, args);
    }
}
