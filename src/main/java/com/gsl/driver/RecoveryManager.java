package com.gsl.driver;

import com.gsl.driver.FailureClassifier.FailureCategory;
import com.gsl.reports.PerformanceSummaryStats;
import com.gsl.reports.RecoveryLogger;

/**
 * "Recovery Manager" — implements the four escalating recovery levels. Gated strictly by
 * {@link FailureClassifier}: only {@code NAVIGATION_FAILURE}/{@code SESSION_FAILURE}/
 * {@code BROWSER_FAILURE} ever reach here. {@code TEST_FAILURE} (assertions, DB validation) and
 * {@code ENVIRONMENT_FAILURE} (DB/API down) never do — see {@code WebTest.tearDown()}, which is
 * the call site that checks the category before invoking anything here.
 *
 * Levels 1-3 are exposed for in-method retry use by callers that already have a concrete
 * "retry this action" closure (e.g. a page object that just caught a transient click/timeout
 * failure). Level 4 is what {@code WebTest.tearDown()} uses for a cross-test recovery: rather than
 * trying to resume mid-method, it tears the session down now so the NEXT test's
 * {@code setUp()}/{@code SessionManager.ensureLoggedIn()} does a clean fresh acquire+login —
 * simpler and safer than attempting to splice a recovered session back into a method already
 * past the point of failure.
 */
public final class RecoveryManager {

    private RecoveryManager() {}

    /** Level 1 — retry the same action once, no health-check-driven branching needed. */
    public static boolean retry(String actionLabel, Runnable action) {
        ExecutionContext ctx = ExecutionContext.current();
        ctx.setRecoveryLevel(1);
        ExecutionManager.logState("recovery:level1:before:" + actionLabel);
        RecoveryLogger.started("Recovery Level 1 — retrying '" + actionLabel + "'", 1);
        try {
            action.run();
            RecoveryLogger.step("Retry '" + actionLabel + "'", true);
            ExecutionManager.logState("recovery:level1:after:" + actionLabel);
            return true;
        } catch (Exception e) {
            RecoveryLogger.step("Retry '" + actionLabel + "' — " + e.getMessage(), false);
            ExecutionManager.logState("recovery:level1:after:" + actionLabel);
            return false;
        }
    }

    /** Level 2 — refresh the current page, then let the caller re-run its own restore step. */
    public static boolean refreshAndRestore(String actionLabel, Runnable restoreAction) {
        ExecutionContext ctx = ExecutionContext.current();
        ctx.setRecoveryLevel(2);
        ExecutionManager.logState("recovery:level2:before:" + actionLabel);
        RecoveryLogger.started("Recovery Level 2 — refresh + restore for '" + actionLabel + "'", 2);
        try {
            DriverManager.getDriver().navigate().refresh();
            RecoveryLogger.step("Refresh Page", true);
            restoreAction.run();
            RecoveryLogger.step("Restore '" + actionLabel + "'", true);
            ExecutionManager.logState("recovery:level2:after:" + actionLabel);
            return true;
        } catch (Exception e) {
            RecoveryLogger.step("Refresh + restore '" + actionLabel + "' — " + e.getMessage(), false);
            ExecutionManager.logState("recovery:level2:after:" + actionLabel);
            return false;
        }
    }

    /** Level 3 — re-run full navigation from the current module down, without logging in again. */
    public static boolean restartNavigation(String actionLabel, Runnable navigateFromCurrentModule) {
        ExecutionContext ctx = ExecutionContext.current();
        ctx.setRecoveryLevel(3);
        ExecutionManager.logState("recovery:level3:before:" + actionLabel);
        RecoveryLogger.started("Recovery Level 3 — restart navigation for '" + actionLabel + "' (no re-login)", 3);
        if (!HealthChecker.isSessionAlive()) {
            RecoveryLogger.warn("Recovery Level 3 skipped — session already dead, escalate to Level 4.");
            return false;
        }
        try {
            navigateFromCurrentModule.run();
            RecoveryLogger.step("Restart navigation '" + actionLabel + "'", true);
            ExecutionManager.logState("recovery:level3:after:" + actionLabel);
            return true;
        } catch (Exception e) {
            RecoveryLogger.step("Restart navigation '" + actionLabel + "' — " + e.getMessage(), false);
            ExecutionManager.logState("recovery:level3:after:" + actionLabel);
            return false;
        }
    }

    /**
     * Level 4 — full recovery. Tears down whatever's left of the current thread's session (best
     * effort) and marks the thread so the next {@code setUp()} does a completely fresh
     * acquire+login rather than trying to reuse anything. Never called for {@code TEST_FAILURE}/
     * {@code ENVIRONMENT_FAILURE} — see the class javadoc.
     */
    public static void forceFreshSessionNextTime(FailureCategory reason) {
        long startMs = System.currentTimeMillis();
        ExecutionContext ctx = ExecutionContext.current();
        ctx.setRecoveryLevel(4);
        ExecutionManager.logState("recovery:level4:before:" + reason);
        PerformanceSummaryStats.recordRecovery();
        RecoveryLogger.started(reason.name(), 4);
        SessionManager.closeSessionAndReleaseUser("Level 4 recovery: " + reason);
        RecoveryLogger.step("Browser Closed / User Released", true);
        ctx.setNeedsRecovery(true);
        RecoveryLogger.step("Next test will re-acquire and re-login fresh", true);
        RecoveryLogger.completed(System.currentTimeMillis() - startMs);
        ExecutionManager.logState("recovery:level4:after:" + reason);
    }

    /** Convenience dispatcher for callers that just have a category and want the right level tried. */
    public static boolean attempt(FailureCategory category, String actionLabel, Runnable retryAction) {
        switch (category) {
            case NAVIGATION_FAILURE:
                if (HealthChecker.canRecoverWithoutLogin()) {
                    // Level 1 → Level 2 → Level 3 before escalating — Level 3's own internal
                    // isSessionAlive() re-check is redundant here (canRecoverWithoutLogin() already
                    // confirmed it) but harmless to leave as defensive double-checking.
                    return retry(actionLabel, retryAction)
                            || refreshAndRestore(actionLabel, retryAction)
                            || restartNavigation(actionLabel, retryAction);
                }
                forceFreshSessionNextTime(category);
                return false;
            case SESSION_FAILURE:
            case BROWSER_FAILURE:
                forceFreshSessionNextTime(category);
                return false;
            case TEST_FAILURE:
            case ENVIRONMENT_FAILURE:
            default:
                // Never touch the session for these — caller shouldn't have reached here.
                return false;
        }
    }
}
