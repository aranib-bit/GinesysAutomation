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

    /** How far navigation has progressed for this session, coarsest-to-finest — used by recovery to decide how much can be skipped when restoring state. */
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

    /** Whether this thread's session has completed application login. */
    public boolean isLoggedIn()                  { return loggedIn; }
    public void setLoggedIn(boolean loggedIn)     { this.loggedIn = loggedIn; }

    /** The credential currently logged in on this session, or {@code null} before login. */
    public UserCredential getUser()               { return user; }
    public void setUser(UserCredential user)      { this.user = user; }

    /** The {@link HomePage} returned by the last successful login on this session. */
    public HomePage getHomePage()                 { return homePage; }
    public void setHomePage(HomePage homePage)    { this.homePage = homePage; }

    /** Whether {@link #getUser()} was acquired from {@code UserPool} (vs. a fixed/config-supplied credential). */
    public boolean isUserFromPool()               { return userFromPool; }
    public void setUserFromPool(boolean fromPool) { this.userFromPool = fromPool; }

    /** Free-form key (e.g. "FINANCE") of the module last navigated to — not the module's own enum type, so this class stays decoupled from every module. */
    public String getCurrentModule()                    { return currentModule; }
    /** Free-form key of the submodule last navigated to, or {@code null} if navigation hasn't gone past module level. */
    public String getCurrentSubModule()                 { return currentSubModule; }
    /** The page object returned by the last {@code navigateToSubMenu} call, cached so a later row can skip re-navigating. */
    public Object getCurrentSubModulePage()             { return currentSubModulePage; }
    /** Free-form key of the page last navigated to, or {@code null} if navigation hasn't gone past submodule level. */
    public String getCurrentPage()                      { return currentPage; }

    /** Records a module-level navigation checkpoint, clearing any deeper (submodule/page) state that no longer applies. */
    public void setModuleCheckpoint(String module) {
        this.currentModule = module;
        this.currentSubModule = null;
        this.currentSubModulePage = null;
        this.lastStableCheckpoint = Checkpoint.MODULE;
    }

    /** Records a submodule-level navigation checkpoint, caching the resolved page object for reuse by a later row. */
    public void setSubModuleCheckpoint(String subModule, Object subModulePage) {
        this.currentSubModule = subModule;
        this.currentSubModulePage = subModulePage;
        this.lastStableCheckpoint = Checkpoint.SUBMODULE;
    }

    /** Records a page-level navigation checkpoint — the finest-grained {@link Checkpoint}. */
    public void setCurrentPage(String page) {
        this.currentPage = page;
        this.lastStableCheckpoint = Checkpoint.PAGE;
    }

    /** Whether {@link RecoveryManager} has flagged this session for a forced fresh acquire+login next time. */
    public boolean isNeedsRecovery()              { return needsRecovery; }
    public void setNeedsRecovery(boolean needsRecovery) { this.needsRecovery = needsRecovery; }

    /** The recovery level ({@code RecoveryManager} 1-4) last attempted on this session, if any. */
    public int getRecoveryLevel()                 { return recoveryLevel; }
    public void setRecoveryLevel(int recoveryLevel) { this.recoveryLevel = recoveryLevel; }

    /** Number of consecutive retry attempts recorded for the current action. */
    public int getRetryCount()                    { return retryCount; }
    public void incrementRetryCount()              { this.retryCount++; }
    public void resetRetryCount()                  { this.retryCount = 0; }

    /** The finest-grained {@link Checkpoint} reached since the last {@link #resetSession()}. */
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

    /** True once this thread has ever completed a real login — deliberately never cleared by {@link #resetSession()}. */
    public boolean hasEverLoggedIn()               { return hasEverLoggedIn; }
    public void setHasEverLoggedIn(boolean v)      { this.hasEverLoggedIn = v; }

    /** True for the short window spanning this thread's very first login + first navigation (cold-start cost attribution). */
    public boolean isColdStartInProgress()         { return coldStartInProgress; }
    public void setColdStartInProgress(boolean v)  { this.coldStartInProgress = v; }

    /** "SimpleClassName.methodName" of the test that triggered the current cold start, for attribution logging. */
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
