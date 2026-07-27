package com.gsl.driver;

import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.reports.PerformanceSummaryStats;
import com.gsl.reports.PoolAttributionStats;
import com.gsl.reports.SessionLogger;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;

/**
 * "Session Manager" — login/logout/session lifecycle, pinned to the CURRENT THREAD rather than
 * to a single test method. Each parallel worker thread already gets its own driver
 * ({@link DriverManager}'s ThreadLocal); this class is what lets a *second* test method landing
 * on the same thread reuse the login that a *first* one already established, instead of every
 * method paying for its own browser-launch + login + navigation.
 *
 * User/driver ACQUISITION still happens in {@code WebTest.setUp()} exactly as before — this class
 * only owns the actual application login step and the decision of whether that step is needed at
 * all. {@code WebTest.setUp()}/{@code tearDown()} consult {@link ExecutionContext} to decide
 * whether to skip user-acquire/driver-init too (see WebTest for that half of the picture).
 */
public final class SessionManager {

    private SessionManager() {}

    /**
     * Returns an already-logged-in {@link HomePage} for the current thread, reusing the cached
     * session if {@link ExecutionContext} says one exists and {@link HealthChecker} confirms it's
     * still alive. Otherwise performs a real login using the user {@code WebTest.setUp()} already
     * placed on {@link DriverManager} for this thread, and caches the result.
     *
     * Callers replace their old {@code new LoginPage().loginToApplication(user, pass)} call with
     * this — same return type, same effect the first time, free on every reuse after that.
     */
    public static HomePage ensureLoggedIn(UserCredential user) {
        ExecutionContext ctx = ExecutionContext.current();

        if (ctx.isLoggedIn() && !ctx.isNeedsRecovery()
                && HealthChecker.isBrowserAlive() && HealthChecker.isSessionAlive()) {
            SessionLogger.sessionReused(user.username());
            return ctx.getHomePage();
        }

        long startMs = System.currentTimeMillis();
        boolean coldStart = ctx.isColdStartInProgress();
        if (coldStart) {
            SessionLogger.suiteSetupLogin(user.username());
        } else {
            SessionLogger.loginStarted(user.username());
        }
        HomePage home = new LoginPage().loginToApplication(user.username(), user.password());
        SessionLogger.loginSuccess(user.username());
        SessionLogger.landingPageLoaded();
        long loginElapsedMs = System.currentTimeMillis() - startMs;
        SessionLogger.sessionTime(loginElapsedMs);
        PerformanceSummaryStats.recordSessionCreated(user.username());
        if (coldStart) {
            // Login is the first half of this thread's one-time warm-up; the navigation step that
            // follows (NavigationUtils) tags its own timing into the SAME bucket and clears the
            // flag once done — see PoolAttributionStats.
            PoolAttributionStats.recordColdStartWarmup(ctx.getColdStartLabel(), loginElapsedMs);
        }

        ctx.setLoggedIn(true);
        ctx.setNeedsRecovery(false);
        ctx.setUser(user);
        ctx.setHomePage(home);
        ctx.setUserFromPool(true); // WebTest.setUp()'s fresh-acquire path is UserPool-mode by default
        ctx.startLease(); // starts this session's bounded-reuse clock/counter (see SessionPool)
        ctx.setHasEverLoggedIn(true);
        return home;
    }

    /**
     * Best-effort logout — used only by end-of-block cleanup ({@code TestListener.onFinish}) and
     * by the tear-down-now path after a genuine session/browser failure, never after a plain
     * business/DB-validation failure (that path leaves the session untouched for the next test).
     */
    public static void closeSessionAndReleaseUser(String reasonLabel) {
        ExecutionContext ctx = ExecutionContext.current();
        if (!ctx.isLoggedIn() && ctx.getUser() == null) {
            return; // nothing to close on this thread
        }

        SessionLogger.sessionClosed(reasonLabel, ctx.getUser() != null ? ctx.getUser().username() : "unknown");

        try {
            if (HealthChecker.isBrowserAlive() && HealthChecker.isSessionAlive() && ctx.getHomePage() != null) {
                new HomePage().logoutFromApplication();
            }
        } catch (Exception e) {
            SessionLogger.warn("Logout during close-session threw (continuing to quit driver): {}",
                    e.getMessage());
        }

        try {
            Driver.quitDriver();
        } catch (Exception e) {
            SessionLogger.warn("Driver.quitDriver() during close-session threw: {}", e.getMessage());
        }

        try {
            if (ctx.isUserFromPool() && ctx.getUser() != null) {
                UserPool.releaseSet1User(ctx.getUser());
            }
        } finally {
            DriverManager.unloadCurrentUser();
            // Reconcile SessionPool bookkeeping: this session died mid-row (Level-4 recovery or
            // explicit close), so the row's later giveBack must drop it, not re-queue a corpse.
            SessionPool.notifyClosed(Thread.currentThread().getId());
            ctx.resetSession();
        }
    }
}
