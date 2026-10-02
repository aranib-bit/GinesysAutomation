package com.gsl.driver;

import com.gsl.browser.CustomMetricsObserverInjector;
import com.gsl.browser.injector.FrontendDiagnosticsInjectorFactory;
import com.gsl.config.ConfigFactory;
import com.gsl.exceptions.UserPoolAcquisitionTimeoutException;
import com.gsl.network.NetworkCapture;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.pages.HomePage;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.Keys;
import org.openqa.selenium.interactions.Actions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Borrow/return pool of logged-in browser sessions — the piece that decouples sessions from
 * threads. Under {@code runInParallel=true}, TestNG runs each @Test method's data rows on a
 * SHORT-LIVED per-method thread pool; binding sessions to threads (the old ThreadLocal-only
 * model) meant dying/idling threads took their users and browsers with them. Here, any
 * data-provider thread borrows a session for exactly one row ({@code WebTest.setUp}) and returns
 * it afterward ({@code WebTest.tearDown}); rows from ALL methods interleave across all sessions
 * work-stealing style, and every session keeps its login + navigation cache between rows.
 *
 * <p>A session is only ever owned by one thread at a time (idle-queue transfer provides the
 * happens-before edge — see {@link PooledSession}). {@code capacity == UserPool UI-user count},
 * so UserPool waits only ever happen during initial pool growth, never steady-state.
 *
 * <p>CONSTRAINT: one static pool per JVM, shared by every concurrently-running {@code <test>} block
 * under {@code parallel="tests"} (deliberately — this is what makes cross-class session work-stealing
 * possible). {@link #drainAll} therefore runs exactly ONCE, at suite end (after every block has
 * finished), not per-block — draining at any one block's own finish while a sibling block is still
 * running would destroy sessions genuinely still borrowed elsewhere (see Round 22 of the project's
 * plan for the live evidence: an {@code UnreachableBrowserException} traced back to exactly this).
 */
public final class SessionPool {

    private static final LinkedBlockingQueue<PooledSession> IDLE = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<Long, PooledSession> BORROWED = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL = new AtomicInteger();
    private static final AtomicInteger SESSION_SEQ = new AtomicInteger();
    private static volatile int cachedCapacity = -1;
    private static volatile Semaphore coldStartGate;

    private SessionPool() {}

    // ── borrow ────────────────────────────────────────────────────────────────

    /**
     * Blocks until a session is available (or one can be created), binds it to the current
     * thread, and returns it. Throws {@link UserPoolAcquisitionTimeoutException} after
     * {@code session.borrow.timeout.seconds} of fruitless waiting.
     */
    public static PooledSession borrow(String qualifiedLabel) {
        long startMs = System.currentTimeMillis();
        long warnAtMs = ConfigFactory.getExecutionConfig().poolWarnAtSeconds() * 1000L;
        long timeoutMs = ConfigFactory.getExecutionConfig().sessionBorrowTimeoutSeconds() * 1000L;
        boolean warned = false;

        while (true) {
            PooledSession s;
            try {
                s = IDLE.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UserPoolAcquisitionTimeoutException(
                        "[SessionPool] Interrupted while waiting to borrow a session for '"
                                + qualifiedLabel + "'.");
            }

            if (s != null) {
                bindToCurrentThread(s, qualifiedLabel);
                if (isReusable(s)) {
                    if (s.ctx().isLoggedIn()) {
                        s.ctx().recordLeaseReuse();
                    }
                    ReportLogger.session("[{}] Borrowed by [T-{}] for '{}' — user='{}', reuse #{}, "
                            + "previous test on this session='{}'.",
                            s.label(), Thread.currentThread().getId(), qualifiedLabel,
                            s.uiUser().username(), s.ctx().getLeaseRowCount(), s.previousBorrowerLabel());
                    return s;
                }
                // Unhealthy / lease-expired / flagged for recovery: destroy and loop — the freed
                // capacity slot is published via TOTAL so ANY waiter (not just us) can claim it.
                unbindFromCurrentThread();
                destroy(s, "failed reuse validation on borrow (health/lease/recovery)");
                continue;
            }

            int cur = TOTAL.get();
            if (cur < capacity() && TOTAL.compareAndSet(cur, cur + 1)) {
                try {
                    // Round 14: throttles concurrent NEW-browser launches (not reused-session
                    // borrows, which never reach this branch) — see coldStartGate()'s own comment.
                    // Acquired inside the capacity-reserved branch (after the CAS above) so a
                    // thread blocked here still correctly holds its own reserved TOTAL slot.
                    try {
                        coldStartGate().acquire();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new UserPoolAcquisitionTimeoutException(
                                "[SessionPool] Interrupted while waiting for a cold-start slot for '"
                                        + qualifiedLabel + "'.");
                    }
                    PooledSession created;
                    try {
                        created = createSession(qualifiedLabel);
                    } finally {
                        coldStartGate().release();
                    }
                    bindToCurrentThread(created, qualifiedLabel);
                    ReportLogger.session("[{}] Created by [T-{}] for '{}' — user='{}' ({}/{} sessions, "
                            + "previous test on this session='{}').",
                            created.label(), Thread.currentThread().getId(), qualifiedLabel,
                            created.uiUser().username(), TOTAL.get(), capacity(), created.previousBorrowerLabel());
                    return created;
                } catch (RuntimeException e) {
                    TOTAL.decrementAndGet();
                    throw e;
                }
            }

            long waitedMs = System.currentTimeMillis() - startMs;
            if (!warned && waitedMs >= warnAtMs) {
                warned = true;
                String snapshot = borrowedSnapshot();
                Log.warn("[SessionPool] [T-{}] Still waiting for a session after {}s for '{}'. {}",
                        Thread.currentThread().getId(), waitedMs / 1000, qualifiedLabel, snapshot);
                ReportLogger.sessionWarn("[SessionPool] [T-{}] Still waiting for a session after {}s "
                        + "for '{}'. {}", Thread.currentThread().getId(), waitedMs / 1000,
                        qualifiedLabel, snapshot);
            }
            if (waitedMs >= timeoutMs) {
                String snapshot = borrowedSnapshot();
                Log.error("[SessionPool] [T-{}] Borrow TIMEOUT after {}s for '{}'. {}",
                        Thread.currentThread().getId(), waitedMs / 1000, qualifiedLabel, snapshot);
                ReportLogger.sessionWarn("[SessionPool] [T-{}] Borrow TIMEOUT after {}s for '{}'. {}",
                        Thread.currentThread().getId(), waitedMs / 1000, qualifiedLabel, snapshot);
                throw new UserPoolAcquisitionTimeoutException(
                        "[SessionPool] No session became available within "
                                + (timeoutMs / 1000) + "s for '" + qualifiedLabel + "'. " + snapshot);
            }
        }
    }

    // ── current-borrow introspection (for report/log correlation) ───────────────

    /**
     * Label ("S-3") of the session the CURRENT thread is holding right now, or {@code null} if
     * nothing is borrowed. Called from {@code TestListener.onTestStart} to stamp the Extent
     * report / correlation log with which pooled browser ran this test.
     */
    public static String currentSessionLabel() {
        PooledSession s = BORROWED.get(Thread.currentThread().getId());
        return s != null ? s.label() : null;
    }

    /**
     * Qualified label of whichever test last ran on the CURRENT thread's borrowed session before
     * this borrow — "none (first use)" if this is the session's first-ever borrow, or {@code
     * null} if nothing is borrowed. Lets a reader correlate a stuck/failed browser to exactly
     * which test ran on it immediately before the current one.
     */
    public static String currentSessionPreviousLabel() {
        PooledSession s = BORROWED.get(Thread.currentThread().getId());
        return s != null ? s.previousBorrowerLabel() : null;
    }

    /**
     * Upgrades the CURRENT thread's borrowed session label from the generic
     * "ClassName.methodName" set at borrow time to the rich, per-row Extent title (tcId +
     * human-readable description) once {@code TestListener.onTestStart} has built it. No-op if
     * nothing is borrowed (legacy/non-pool tests). This is what the NEXT borrower of this session
     * will see reported as "previous test on this session".
     */
    public static void updateCurrentLabel(String richLabel) {
        PooledSession s = BORROWED.get(Thread.currentThread().getId());
        if (s != null) {
            s.updateCurrentLabel(richLabel);
        }
    }

    /**
     * Snapshot of every username currently BORROWED (i.e. actively running a test right now, on
     * any thread) mapped to the live label of whichever test is using it — sourced from each
     * {@link PooledSession#lastBorrowerLabel()}, which is refreshed on every borrow AND upgraded
     * by {@link #updateCurrentLabel} once the real per-row title is known. Unlike {@code
     * UserPool.ACTIVE_USERS} (only refreshed at session *creation*), this stays accurate when a
     * session is *reused* across test cases — e.g. test case 1 finishes on user1, the session
     * goes idle, test case 6 later borrows it and calls {@code updateCurrentLabel}: this map then
     * reports user1 → test case 6, not test case 1. Used by {@code TestListener.onTestStart} to
     * build the Extent report's "User Pool Status" table. Covers both the UI and (when present)
     * API user of each borrowed session.
     */
    public static Map<String, String> currentTestCaseIdsByUsername() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (PooledSession s : BORROWED.values()) {
            if (s.uiUser() != null) {
                result.put(s.uiUser().username(), s.lastBorrowerLabel());
            }
            if (s.apiUser() != null) {
                result.put(s.apiUser().username(), s.lastBorrowerLabel());
            }
        }
        return result;
    }

    // ── give back ─────────────────────────────────────────────────────────────

    /**
     * Returns the current thread's borrowed session to the idle queue and fully unbinds the
     * thread. Idempotent — safe to call from a finally even when nothing was borrowed, or when
     * the session was already closed out-of-band (Level-4 recovery via
     * {@code SessionManager.closeSessionAndReleaseUser} → {@link #notifyClosed}).
     */
    public static void giveBack() {
        PooledSession s = BORROWED.remove(Thread.currentThread().getId());
        // Dirty-state check runs BEFORE unbind — unbindFromCurrentThread() calls
        // DriverManager.unloadDriver(), after which the probe has no driver to look at.
        if (s != null && !s.isClosed()) {
            resetOrFlagDirtyUi(s);
        }
        unbindFromCurrentThread();
        if (s == null || s.isClosed()) {
            return; // nothing borrowed, or destroyed mid-row — TOTAL already reconciled
        }
        ReportLogger.session("[{}] Returned by [T-{}] — user='{}' stays logged in for the next borrower.",
                s.label(), Thread.currentThread().getId(), s.uiUser().username());
        IDLE.offer(s); // publishes all session-state writes to the next borrower
    }

    /**
     * Out-of-band close notification — {@code SessionManager.closeSessionAndReleaseUser} already
     * quit the driver, released the UI user, and reset the ctx on the OWNING thread; this
     * reconciles the pool's bookkeeping so the row's later {@link #giveBack} drops the session
     * instead of re-queuing a corpse.
     */
    public static void notifyClosed(long threadId) {
        PooledSession s = BORROWED.remove(threadId);
        if (s == null || s.isClosed()) {
            return;
        }
        s.markClosed();
        TOTAL.decrementAndGet();
        if (s.apiUser() != null) {
            UserPool.releaseSet1User(s.apiUser());
        }
        ReportLogger.session("[{}] Closed out-of-band (Level-4 recovery or explicit close) — "
                + "capacity slot freed ({}/{} sessions).", s.label(), TOTAL.get(), capacity());
    }

    // ── suite-end drain ───────────────────────────────────────────────────────

    /**
     * END-OF-SUITE teardown (called once, from {@code TestListener.onFinish(ISuite)} — see Round 22
     * of the project's plan for why this must NOT run per-{@code <test>}-block under
     * {@code parallel="tests"}): logs out and closes EVERY session. Each idle session is bound to the
     * draining thread one at a time (the suite has quiesced and the drain is single-threaded, so
     * page objects resolve the right driver), given a REAL application logout when healthy, then
     * destroyed. Guarantees no orphaned server-side sessions and no leaked users/browsers.
     */
    public static void drainAll(String reason) {
        PooledSession s;
        while ((s = IDLE.poll()) != null) {
            bindToCurrentThread(s, "drain: " + reason);
            try {
                if (s.ctx().isLoggedIn()
                        && HealthChecker.isBrowserAlive() && HealthChecker.isSessionAlive()) {
                    ReportLogger.session("[{}] Logging out user '{}' at drain ({}).",
                            s.label(), s.uiUser().username(), reason);
                    new HomePage().logoutFromApplication();
                }
            } catch (Exception e) {
                Log.warn("[SessionPool] [{}] Logout during drain threw (continuing to quit): {}",
                        s.label(), e.getMessage());
            } finally {
                BORROWED.remove(Thread.currentThread().getId());
                unbindFromCurrentThread();
            }
            destroy(s, reason);
        }

        // Should be empty — every row's tearDown gives back before the suite can finish. A
        // leftover here means a worker thread died mid-row; destroy so nothing leaks anyway.
        for (Map.Entry<Long, PooledSession> stale : BORROWED.entrySet()) {
            PooledSession orphan = BORROWED.remove(stale.getKey());
            if (orphan == null || orphan.isClosed()) {
                continue;
            }
            Log.warn("[SessionPool] [{}] Still marked borrowed by thread {} (label='{}') at drain — "
                    + "destroying without logout.", orphan.label(), stale.getKey(),
                    orphan.lastBorrowerLabel());
            ReportLogger.sessionWarn("[SessionPool] [{}] Leftover borrowed session at drain "
                    + "(thread {}, label='{}') — destroying.", orphan.label(), stale.getKey(),
                    orphan.lastBorrowerLabel());
            destroy(orphan, reason + " (leftover borrowed)");
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** Cached pool size — the number of SET1 UI users available, minus any backfill headroom reserved for {@code DataAvailabilityGuard}'s transient WEBAPI logins. Computed once and reused. */
    private static int capacity() {
        int cap = cachedCapacity;
        if (cap <= 0) {
            int totalUiUsers = UserPool.initialCountOf(UserType.UI);
            int backfillHeadroom = ConfigFactory.getApiConfig().backfillUiPoolHeadroom();
            // Reserve backfillHeadroom UI users EXCLUSIVELY for DataAvailabilityGuard's transient
            // WEBAPI logins (a WEB test's thread may need one MORE UI credential mid-test — see
            // ApiSessionFactory.createForWebApi()) — never hand them to SessionPool for browser
            // concurrency. Without this, padding UsersList.xlsx with spare users for backfill
            // headroom silently inflates SessionPool's capacity (and therefore how many concurrent
            // browser sessions actually open) beyond what <test thread-count> intends — confirmed:
            // adding 2 spare UI users for backfill made 6 browsers open instead of the intended 4,
            // because capacity() derived straight from the raw UserPool UI-user count.
            cap = Math.max(1, totalUiUsers - backfillHeadroom);
            cachedCapacity = cap;
            Log.info("[SessionPool] capacity={} (UI users={} - backfill headroom={})",
                    cap, totalUiUsers, backfillHeadroom);
        }
        return cap;
    }

    /**
     * Caps how many brand-new browser sessions {@link #createSession} may launch AT THE SAME
     * MOMENT. Confirmed live (run {@code 20260925_175304}, right after {@code parallel="tests"}
     * first went live): with this suite running HEADED ({@code headless=false}), several Chrome
     * windows launching within the same instant get clipped/mis-sized by the OS window manager —
     * {@code ViewportEnforcer} reported a mismatch (1350x673 vs. requested 1366x768) on multiple
     * cold starts, plus one outright "remote browser died" crash, all clustered at the exact
     * moment several threads' cold starts overlapped. A HEADLESS run has no real window for the OS
     * to clip — CDP's device-metrics override is authoritative regardless of screen space — so this
     * gate only throttles when headed; headless keeps full pool-capacity concurrency (Docker/CI is
     * unaffected). Headed permit count is configurable
     * ({@code session.headed.coldstart.maxconcurrent}, see
     * {@link com.gsl.config.ExecutionConfig#headedColdStartMaxConcurrent()}), defaulting to this
     * suite's own pre-{@code parallel="tests"} maximum concurrency — not a hardcoded guess.
     */
    private static Semaphore coldStartGate() {
        Semaphore gate = coldStartGate;
        if (gate == null) {
            int permits = ConfigFactory.getBrowserConfig().headless()
                    ? capacity()
                    : ConfigFactory.getExecutionConfig().headedColdStartMaxConcurrent();
            gate = new Semaphore(permits);
            coldStartGate = gate;
            Log.info("[SessionPool] Cold-start concurrency gate: {} permit(s) (headless={})",
                    permits, ConfigFactory.getBrowserConfig().headless());
        }
        return gate;
    }

    /** Validation runs AFTER bind — HealthChecker reads the calling thread's driver binding. */
    /**
     * Last line of defence against handing a dirty page to the next test.
     *
     * <p>{@code WebTest.tearDown}'s Level-4 recovery only fires for NAVIGATION/SESSION/BROWSER
     * failures — a plain assertion failure is classified {@code TEST_FAILURE} and deliberately
     * never touches the session, yet it can just as easily leave an Add/Modify form open. Run
     * 20260807_095328 is the worked example: one test timed out with {@code Add: General Voucher}
     * on screen, and the next three failed clicking the side menu <i>through</i> that form
     * ("not clickable at point (82, 173). Other element would receive the click:
     * {@code combo-1216-labelTextEl}") — a form field label.
     *
     * <p>Two steps, cheapest first:
     * <ol>
     *   <li><b>ESC.</b> A closable ExtJS window dismisses on ESC, returning the app to the list
     *       page underneath — which is the page the navigation cache already believes we are on,
     *       so the cache stays truthful. Non-destructive: it cannot misclick, and if the form is
     *       dirty enough to raise a "discard changes?" confirm, step 2 catches that.</li>
     *   <li><b>Flag for recovery.</b> Still dirty → {@code setNeedsRecovery(true)}, which
     *       {@link #isReusable} already honours, so the next borrower destroys this session and
     *       creates a clean one. No new UI-driving code, no risk of clicking through an unknown
     *       dialog, and no chance of a stale navigation cache surviving.</li>
     * </ol>
     *
     * <p>Best-effort throughout — this runs inside teardown and must never throw into it.
     */
    private static void resetOrFlagDirtyUi(PooledSession s) {
        try {
            if (!HealthChecker.isBrowserAlive() || !HealthChecker.isModalWindowOpen()) {
                return;
            }
            ReportLogger.sessionWarn("[{}] Returned with a modal window still open — the previous test "
                    + "did not close its form. Attempting ESC before reuse.", s.label());
            try {
                new Actions(s.driver()).sendKeys(Keys.ESCAPE).perform();
            } catch (RuntimeException e) {
                Log.warn("[SessionPool] [{}] ESC dismiss threw: {}", s.label(), e.getMessage());
            }

            if (!HealthChecker.isModalWindowOpen()) {
                ReportLogger.session("[{}] Modal dismissed — session is clean and reusable.", s.label());
                return;
            }
            s.ctx().setNeedsRecovery(true);
            ReportLogger.sessionWarn("[{}] Modal still open after ESC — flagged for recovery; the next "
                    + "borrower will destroy this session and create a fresh one.", s.label());
        } catch (RuntimeException e) {
            // A probe that itself failed says nothing good about the session — flag rather than
            // gamble, but never let teardown fail because of it.
            Log.warn("[SessionPool] [{}] Dirty-UI check threw ({}) — flagging for recovery.",
                    s.label(), e.getMessage());
            try {
                s.ctx().setNeedsRecovery(true);
            } catch (RuntimeException ignored) { /* nothing further we can do */ }
        }
    }

    /**
     * Validation runs AFTER bind — {@link HealthChecker} reads the calling thread's driver
     * binding, so this must be called only once {@code s} is already bound to the current thread.
     * A session not yet logged in is reusable as long as the browser itself is alive; a
     * logged-in session must additionally be free of a stuck modal, not flagged for recovery, and
     * not lease-expired.
     */
    private static boolean isReusable(PooledSession s) {
        if (s.isClosed()) {
            return false;
        }
        // Cheap re-check on the borrow side too: giveBack's probe runs on the returning thread and
        // can be skipped entirely (destroy mid-row, an exception before it), so this is the one
        // place guaranteed to run immediately before a session is used again.
        if (HealthChecker.isModalWindowOpen()) {
            Log.warn("[SessionPool] [{}] Refusing reuse — a modal window is open on this session.", s.label());
            return false;
        }
        if (!s.ctx().isLoggedIn()) {
            // Created but never logged in (its first row failed before Step 1, or login is still
            // pending) — reusable as long as the browser itself is alive.
            return HealthChecker.isBrowserAlive();
        }
        return !s.ctx().isNeedsRecovery()
                && HealthChecker.isBrowserAlive()
                && HealthChecker.isSessionAlive()
                && !s.ctx().isLeaseExpired();
    }

    /**
     * Creates a new session on the calling thread: acquire user(s) → fresh session context with
     * cold-start flags → launch browser (Driver.initDriver: create+instrument driver, open app
     * URL, start network capture) → per-driver diagnostics injection. LOGIN IS LAZY — the row's
     * Step-1 {@code SessionManager.ensureLoggedIn()} performs it, so recordSessionCreated and
     * cold-start attribution fire exactly as before.
     */
    private static PooledSession createSession(String qualifiedLabel) {
        UserCredential ui = UserPool.acquireSet1User(UserType.UI, qualifiedLabel);
        if (ui == null) {
            throw new org.testng.SkipException(
                    "[SessionPool] No SET1 UI user available for '" + qualifiedLabel + "'.");
        }
        try {
            UserCredential api = UserPool.hasUsersOfType(UserType.API)
                    ? UserPool.acquireSet1User(UserType.API, qualifiedLabel)
                    : null;
            try {
                ExecutionContext ctx = ExecutionContext.newSessionContext();
                ctx.setColdStartInProgress(true);
                ctx.setColdStartLabel(qualifiedLabel);
                // Temporary thread state for initDriver: heartbeats during browser launch need the
                // user, and initDriver stores the instrumented driver in this thread's binding.
                DriverManager.setCurrentUser(ui);
                int sessionId = SESSION_SEQ.incrementAndGet();
                // Must be assigned BEFORE initDriver — BrowserOptions.chrome()/edge() read the
                // current thread's download dir at browser-launch time.
                java.nio.file.Path downloadDir = SessionDownloadDirectory.assignNew(sessionId);
                Driver.initDriver();
                org.openqa.selenium.WebDriver driver = DriverManager.getDriver();
                NetworkCapture capture = NetworkCaptureManager.get();
                String remoteSessionId = TestContextManager.getSessionId();

                if (ConfigFactory.getDiagnosticsConfig().customMetricsEnabled()) {
                    CustomMetricsObserverInjector.inject(driver);
                }
                if (ConfigFactory.getDiagnosticsConfig().frontendDiagnosticsEnabled()) {
                    FrontendDiagnosticsInjectorFactory.get().inject(driver);
                }

                return new PooledSession(sessionId, driver, ui, api,
                        ctx, capture, remoteSessionId, downloadDir);
            } catch (RuntimeException e) {
                try {
                    Driver.quitDriver();
                } catch (Exception quitEx) {
                    Log.warn("[SessionPool] quitDriver during create-unwind threw: {}", quitEx.getMessage());
                }
                if (api != null) {
                    UserPool.releaseSet1User(api);
                }
                throw e;
            }
        } catch (RuntimeException e) {
            UserPool.releaseSet1User(ui);
            throw e;
        }
    }

    /**
     * The per-borrow identity re-stamp — EVERYTHING session-scoped that the row will read must
     * point at this session before the test body runs. Most load-bearing:
     * {@code DriverManager.setCurrentUser} — mid-row DB loads resolve login → hrdemp.abbre →
     * ecode → site/OU codes from it for DB validation, and HeartbeatListener keys heartbeats by
     * it. {@code TestContextManager.setUiUser} stamps the login name into the Log4j2
     * ThreadContext so every log line of the row carries [user=...].
     */
    private static void bindToCurrentThread(PooledSession s, String qualifiedLabel) {
        long tid = Thread.currentThread().getId();
        BORROWED.put(tid, s);
        DriverManager.bindExistingDriver(s.driver()); // instrumented already — never re-wrap
        DriverManager.setCurrentUser(s.uiUser());
        if (s.apiUser() != null) {
            DriverManager.setApiUser(s.apiUser());
        }
        NetworkCaptureManager.set(s.capture());
        SessionDownloadDirectory.set(s.downloadDir());
        ExecutionContext.attach(s.ctx());
        TestContextManager.setUiUser(s.uiUser().username());
        if (s.apiUser() != null) {
            TestContextManager.setApiUser(s.apiUser().username());
        }
        if (s.remoteSessionId() != null) {
            TestContextManager.setSessionId(s.remoteSessionId());
        }
        UserPool.heartbeat(UserType.UI, s.uiUser().username());
        s.stampBorrower(tid, qualifiedLabel);
    }

    /** Reverses {@link #bindToCurrentThread} — detaches the execution context and clears all thread-local driver/user/capture/download-dir bindings for the calling thread. */
    private static void unbindFromCurrentThread() {
        ExecutionContext.detach();
        DriverManager.unloadDriver();
        DriverManager.unloadCurrentUser();
        DriverManager.unloadApiUser();
        NetworkCaptureManager.remove();
        SessionDownloadDirectory.clear();
    }

    /**
     * Quits and fully releases a session. No app-level logout here — mid-run destroys happen on
     * broken/expired sessions where quit is the only safe action; {@link #drainAll} does the real
     * logout for healthy sessions at suite end.
     */
    private static void destroy(PooledSession s, String reason) {
        if (s.isClosed()) {
            return;
        }
        s.markClosed();
        try {
            if (s.capture() != null) {
                s.capture().stop();
            }
        } catch (Exception e) {
            Log.warn("[SessionPool] [{}] capture.stop() threw during destroy: {}", s.label(), e.getMessage());
        }
        try {
            s.driver().quit();
        } catch (Exception e) {
            Log.warn("[SessionPool] [{}] driver.quit() threw during destroy: {}", s.label(), e.getMessage());
        }
        try {
            UserPool.releaseSet1User(s.uiUser());
            if (s.apiUser() != null) {
                UserPool.releaseSet1User(s.apiUser());
            }
        } finally {
            s.ctx().resetSession();
            TOTAL.decrementAndGet();
        }
        ReportLogger.session("[{}] Destroyed — {} ({}/{} sessions).",
                s.label(), reason, TOTAL.get(), capacity());
    }

    private static String borrowedSnapshot() {
        if (BORROWED.isEmpty()) {
            return "No sessions currently borrowed; pool at " + TOTAL.get() + "/" + capacity() + ".";
        }
        StringBuilder sb = new StringBuilder("Currently borrowed: ");
        long now = System.currentTimeMillis();
        BORROWED.forEach((tid, s) -> sb.append(String.format("[%s user='%s' thread=%d label='%s' heldFor=%ds] ",
                s.label(), s.uiUser().username(), tid, s.lastBorrowerLabel(),
                (now - s.lastBorrowMs()) / 1000)));
        return sb.toString().trim();
    }
}
