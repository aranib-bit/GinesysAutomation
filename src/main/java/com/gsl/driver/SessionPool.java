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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
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
 * <p>CONSTRAINT: one static pool per JVM, drained at each {@code <test>}-block boundary
 * ({@link #drainAll}) — assumes suites run their blocks sequentially ({@code parallel="none"}),
 * which all current suites do.
 */
public final class SessionPool {

    private static final LinkedBlockingQueue<PooledSession> IDLE = new LinkedBlockingQueue<>();
    private static final ConcurrentHashMap<Long, PooledSession> BORROWED = new ConcurrentHashMap<>();
    private static final AtomicInteger TOTAL = new AtomicInteger();
    private static final AtomicInteger SESSION_SEQ = new AtomicInteger();
    private static volatile int cachedCapacity = -1;

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
                    PooledSession created = createSession(qualifiedLabel);
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

    // ── give back ─────────────────────────────────────────────────────────────

    /**
     * Returns the current thread's borrowed session to the idle queue and fully unbinds the
     * thread. Idempotent — safe to call from a finally even when nothing was borrowed, or when
     * the session was already closed out-of-band (Level-4 recovery via
     * {@code SessionManager.closeSessionAndReleaseUser} → {@link #notifyClosed}).
     */
    public static void giveBack() {
        PooledSession s = BORROWED.remove(Thread.currentThread().getId());
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

    // ── block-end drain ───────────────────────────────────────────────────────

    /**
     * END-OF-BLOCK teardown: logs out and closes EVERY session. Each idle session is bound to the
     * draining thread one at a time (the block has quiesced and the drain is single-threaded, so
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

        // Should be empty — every row's tearDown gives back before the block can finish. A
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

    private static int capacity() {
        int cap = cachedCapacity;
        if (cap <= 0) {
            cap = UserPool.initialCountOf(UserType.UI);
            cachedCapacity = cap;
        }
        return cap;
    }

    /** Validation runs AFTER bind — HealthChecker reads the calling thread's driver binding. */
    private static boolean isReusable(PooledSession s) {
        if (s.isClosed()) {
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
     * logout for healthy sessions at block end.
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
