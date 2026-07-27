package com.gsl.driver;

import com.gsl.reports.ReportLogger;

/**
 * Minimal "Execution Manager" — gives the architecture's §3.1 component concrete substance without
 * pulling session/recovery orchestration (still owned by {@code WebTest}/{@code RecoveryManager})
 * into a new class. Its one job: print a full, honest snapshot of {@link ExecutionContext}'s
 * current state at each major lifecycle transition, so the report shows exactly what the framework
 * believed was true at that point — no framework activity is inferred, only what's actually there.
 */
public final class ExecutionManager {

    private ExecutionManager() {}

    /**
     * Logs the current thread's full execution state. Safe to call at any point, including before
     * a browser/session exists — {@link HealthChecker#isBrowserAlive()} handles a null driver, and
     * the loader check is skipped entirely when the browser isn't alive rather than risking a probe
     * against a dead/missing driver.
     *
     * @param checkpointLabel human-readable label for where in the lifecycle this snapshot was taken
     *                        (e.g. "setUp:reuse", "navigation:Finance/Operations", "tearDown:start")
     */
    public static void logState(String checkpointLabel) {
        ExecutionContext ctx = ExecutionContext.current();
        boolean browserAlive = HealthChecker.isBrowserAlive();
        boolean loaderVisible = browserAlive && HealthChecker.isLoaderVisible();

        ReportLogger.framework(
                "[ExecutionManager] State @ {} — BrowserAlive={} | LoggedIn={} | CurrentModule={} | "
                + "CurrentSubModule={} | CurrentPage={} | LoaderVisible={} | RecoveryLevel={} | "
                + "RetryCount={} | LastStableCheckpoint={}",
                checkpointLabel, browserAlive, ctx.isLoggedIn(), ctx.getCurrentModule(),
                ctx.getCurrentSubModule(), ctx.getCurrentPage(), loaderVisible, ctx.getRecoveryLevel(),
                ctx.getRetryCount(), ctx.getLastStableCheckpoint());
    }
}
