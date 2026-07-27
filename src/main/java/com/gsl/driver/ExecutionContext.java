package com.gsl.driver;

import com.gsl.config.ConfigFactory;
import com.gsl.pages.HomePage;
import com.gsl.testdata.UserCredential;

/**
 * Execution state for one browser session — the "State Manager" component. Tracks what
 * {@link SessionManager} and navigation helpers have already established for a SESSION so a later
 * test row using that session can skip redundant login/navigation instead of restarting from
 * scratch.
 *
 * Scope model (SessionPool era): an instance is OWNED by a {@link PooledSession} and travels with
 * it between threads — {@link SessionPool} attaches it to whichever thread borrows the session
 * ({@link #attach}) and detaches it on return ({@link #detach}). {@link #current()} still resolves
 * per-thread via ThreadLocal so the ~dozen existing call sites (NavigationUtils, SessionManager,
 * RecoveryManager, ExecutionManager, WebTest) are unchanged; {@code withInitial} keeps legacy
 * paths (Mobile/API/non-pool tests) null-safe by lazily creating a throwaway per-thread instance.
 */
public final class ExecutionContext {

    public enum Checkpoint { NONE, LOGIN, MODULE, SUBMODULE, PAGE }

    private static final ThreadLocal<ExecutionContext> CONTEXT = ThreadLocal.withInitial(ExecutionContext::new);

    private boolean loggedIn;
    private UserCredential user;
    private HomePage homePage;
    private boolean userFromPool;

    private String currentModule;     // e.g. "FINANCE" — free-form key, not the enum itself,
    private String currentSubModule;  // to avoid this class depending on every module's enum type
    private Object currentSubModulePage; // cached page object returned by the last navigateToSubMenu call
    private String currentPage;

    private boolean needsRecovery;
    private int recoveryLevel;
    private int retryCount;
    private Checkpoint lastStableCheckpoint = Checkpoint.NONE;

    // ── Session lease — bounds how long a session can be reused before this thread voluntarily
    // cycles it (real close + fresh acquire + relogin), even with zero failures. Without this,
    // "defer logout until failure" can gridlock a parallel="methods" pool: once every thread holds
    // a user and nothing fails, no permit is ever returned. See WebTest.setUp()/SessionManager.
    private long sessionStartMs;
    private int leaseRowCount;

    // ── Cold-start attribution — distinguishes "this thread's very first-ever session" (real
    // suite/class setup cost: browser launch, login, initial navigation) from "this thread
    // cycling to a new session mid-suite" (lease expiry/recovery — normal session activity, not
    // setup). hasEverLoggedIn is deliberately NEVER reset by resetSession() so it stays true for
    // the rest of this thread's life once set; coldStartInProgress is a short-lived flag spanning
    // exactly the first login + first navigation, cleared once that navigation completes.
    private boolean hasEverLoggedIn;
    private boolean coldStartInProgress;
    private String coldStartLabel; // "SimpleClassName.methodName" of the test that triggered cold start

    private ExecutionContext() {}

    public static ExecutionContext current() {
        return CONTEXT.get();
    }

    /** Clears all state for the current thread — call once at end-of-block cleanup. */
    public static void clear() {
        CONTEXT.remove();
    }

    /** Creates a session-owned instance for {@link SessionPool} — NOT bound to any thread yet. */
    public static ExecutionContext newSessionContext() {
        return new ExecutionContext();
    }

    /**
     * Binds a session-owned context to the CURRENT thread so every {@link #current()} call during
     * this row resolves to the borrowed session's state. Only {@link SessionPool} calls this.
     */
    public static void attach(ExecutionContext ctx) {
        CONTEXT.set(ctx);
    }

    /** Unbinds the current thread's context on session return — the instance lives on in the pool. */
    public static void detach() {
        CONTEXT.remove();
    }

    public boolean isLoggedIn()                  { return loggedIn; }
    public void setLoggedIn(boolean loggedIn)     { this.loggedIn = loggedIn; }

    public UserCredential getUser()               { return user; }
    public void setUser(UserCredential user)      { this.user = user; }

    public HomePage getHomePage()                 { return homePage; }
    public void setHomePage(HomePage homePage)    { this.homePage = homePage; }

    public boolean isUserFromPool()               { return userFromPool; }
    public void setUserFromPool(boolean fromPool) { this.userFromPool = fromPool; }

    public String getCurrentModule()                    { return currentModule; }
    public String getCurrentSubModule()                 { return currentSubModule; }
    public Object getCurrentSubModulePage()             { return currentSubModulePage; }
    public String getCurrentPage()                      { return currentPage; }

    public void setModuleCheckpoint(String module) {
        this.currentModule = module;
        this.currentSubModule = null;
        this.currentSubModulePage = null;
        this.lastStableCheckpoint = Checkpoint.MODULE;
    }

    public void setSubModuleCheckpoint(String subModule, Object subModulePage) {
        this.currentSubModule = subModule;
        this.currentSubModulePage = subModulePage;
        this.lastStableCheckpoint = Checkpoint.SUBMODULE;
    }

    public void setCurrentPage(String page) {
        this.currentPage = page;
        this.lastStableCheckpoint = Checkpoint.PAGE;
    }

    public boolean isNeedsRecovery()              { return needsRecovery; }
    public void setNeedsRecovery(boolean needsRecovery) { this.needsRecovery = needsRecovery; }

    public int getRecoveryLevel()                 { return recoveryLevel; }
    public void setRecoveryLevel(int recoveryLevel) { this.recoveryLevel = recoveryLevel; }

    public int getRetryCount()                    { return retryCount; }
    public void incrementRetryCount()              { this.retryCount++; }
    public void resetRetryCount()                  { this.retryCount = 0; }

    public Checkpoint getLastStableCheckpoint()   { return lastStableCheckpoint; }

    /** Call once per real login — starts the lease clock and resets the reuse counter. */
    public void startLease() {
        this.sessionStartMs = System.currentTimeMillis();
        this.leaseRowCount = 0;
    }

    /** Call once per deferred-reuse pass (a row that skipped acquire/login via the fast path). */
    public void recordLeaseReuse() {
        this.leaseRowCount++;
    }

    /** Rows served since the last real login — SessionPool logs it per borrow. */
    public int getLeaseRowCount() {
        return leaseRowCount;
    }

    /**
     * True once this session has been reused for too many consecutive rows or too long —
     * whichever limit is configured to trip first. A thread whose lease has expired must not take
     * the fast reuse path even if the session is otherwise healthy; it must cycle (real close +
     * fresh acquire + relogin) so the pool sees periodic turnover even when nothing ever fails.
     */
    public boolean isLeaseExpired() {
        int maxRows = ConfigFactory.getExecutionConfig().sessionMaxLeaseRows();
        long maxMs  = ConfigFactory.getExecutionConfig().sessionMaxLeaseSeconds() * 1000L;
        return leaseRowCount >= maxRows || (System.currentTimeMillis() - sessionStartMs) >= maxMs;
    }

    public boolean hasEverLoggedIn()               { return hasEverLoggedIn; }
    public void setHasEverLoggedIn(boolean v)      { this.hasEverLoggedIn = v; }

    public boolean isColdStartInProgress()         { return coldStartInProgress; }
    public void setColdStartInProgress(boolean v)  { this.coldStartInProgress = v; }

    public String getColdStartLabel()              { return coldStartLabel; }
    public void setColdStartLabel(String label)     { this.coldStartLabel = label; }

    /** Resets navigation/login state (e.g. after logout/session close) without needing a new thread. */
    public void resetSession() {
        loggedIn = false;
        user = null;
        homePage = null;
        userFromPool = false;
        currentModule = null;
        currentSubModule = null;
        currentSubModulePage = null;
        currentPage = null;
        needsRecovery = false;
        recoveryLevel = 0;
        retryCount = 0;
        lastStableCheckpoint = Checkpoint.NONE;
        sessionStartMs = 0;
        leaseRowCount = 0;
    }
}
