package com.gsl.driver;

import com.gsl.network.NetworkCapture;
import com.gsl.testdata.UserCredential;
import org.openqa.selenium.WebDriver;

import java.nio.file.Path;

/**
 * One poolable browser session — the portable unit that {@link SessionPool} hands from thread to
 * thread: an instrumented WebDriver, the UI (and optional API) user it belongs to, the
 * session-owned {@link ExecutionContext} (login state, navigation cache, lease, recovery and
 * cold-start flags), and the network-capture handle started with the driver.
 *
 * <h3>Thread-safety argument (sequential ownership)</h3>
 * A session is only ever used by ONE thread at a time: it is either inside {@code SessionPool}'s
 * idle queue, or bound to exactly one borrowing thread. The pool's
 * {@code LinkedBlockingQueue.offer→poll} and {@code ConcurrentHashMap.put→remove} transfers
 * provide the happens-before edges, so plain (non-volatile) fields written by borrower N — most
 * importantly the ExecutionContext navigation checkpoints — are visible to borrower N+1.
 * RemoteWebDriver tolerates strictly-serial use from multiple threads.
 */
public final class PooledSession {

    private final int id;
    private final WebDriver driver;       // ALREADY EventFiringDecorator-instrumented — never re-wrap
    private final UserCredential uiUser;
    private final UserCredential apiUser; // nullable — no API users configured in this environment
    private final ExecutionContext ctx;   // session-owned; attached/detached per borrow
    private final NetworkCapture capture;
    private final String remoteSessionId; // nullable — only RemoteWebDriver sessions have one
    private final long createdMs;
    private final Path downloadDir; // fixed for this browser's lifetime, assigned at creation

    private volatile long lastBorrowerThreadId;
    private volatile String lastBorrowerLabel;
    private volatile String previousBorrowerLabel; // whoever held this session immediately before
    private volatile long lastBorrowMs;
    private volatile boolean closed;

    PooledSession(int id, WebDriver driver, UserCredential uiUser, UserCredential apiUser,
                  ExecutionContext ctx, NetworkCapture capture, String remoteSessionId, Path downloadDir) {
        this.id = id;
        this.driver = driver;
        this.uiUser = uiUser;
        this.apiUser = apiUser;
        this.ctx = ctx;
        this.capture = capture;
        this.remoteSessionId = remoteSessionId;
        this.createdMs = System.currentTimeMillis();
        this.downloadDir = downloadDir;
    }

    public int id()                        { return id; }
    /** Short display label for logs/reports, e.g. {@code "S-3"}. */
    public String label()                  { return "S-" + id; }
    public WebDriver driver()              { return driver; }
    public UserCredential uiUser()         { return uiUser; }
    public UserCredential apiUser()        { return apiUser; }
    public ExecutionContext ctx()          { return ctx; }
    public NetworkCapture capture()        { return capture; }
    public String remoteSessionId()        { return remoteSessionId; }
    public long createdMs()                { return createdMs; }
    public Path downloadDir()              { return downloadDir; }

    public long lastBorrowerThreadId()     { return lastBorrowerThreadId; }
    public String lastBorrowerLabel()      { return lastBorrowerLabel; }
    public long lastBorrowMs()             { return lastBorrowMs; }

    /** Qualified label of whoever held this session immediately before the current borrower —
     *  "none (first use)" the first time a session is ever borrowed. Lets a reader correlate a
     *  stuck/failed browser to exactly which test ran on it last, per-session. */
    public String previousBorrowerLabel() {
        return previousBorrowerLabel != null ? previousBorrowerLabel : "none (first use)";
    }

    /** Records a new borrow: captures the outgoing {@link #lastBorrowerLabel} as {@link #previousBorrowerLabel} BEFORE overwriting it with the new borrower's identity. */
    void stampBorrower(long threadId, String label) {
        this.previousBorrowerLabel = this.lastBorrowerLabel; // capture BEFORE overwrite
        this.lastBorrowerThreadId = threadId;
        this.lastBorrowerLabel = label;
        this.lastBorrowMs = System.currentTimeMillis();
    }

    /**
     * Upgrades the label of the CURRENT borrow from the generic method-level qualifier
     * ({@code WebTest.setUp} only knows "ClassName.methodName" at borrow time — the per-row tcId
     * and human-readable description aren't built until {@code TestListener.onTestStart}, which
     * runs after setUp) to the rich Extent title (e.g. "[FIN-AP-ADD-DOC-05] Verify that an AP
     * Voucher is created successfully using..."). Does NOT touch {@link #previousBorrowerLabel} —
     * that was already correctly captured at bind time; this only changes what the NEXT borrower
     * will see as "previous test on this session" once the current row finishes.
     */
    void updateCurrentLabel(String richLabel) {
        this.lastBorrowerLabel = richLabel;
    }

    public boolean isClosed()              { return closed; }
    void markClosed()                      { this.closed = true; }
}
