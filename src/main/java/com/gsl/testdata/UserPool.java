package com.gsl.testdata;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ExecutionConfig;
import com.gsl.exceptions.UserPoolAcquisitionTimeoutException;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Log;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe user pool for distributing credentials across parallel test threads.
 *
 * <h3>SET-1 (independent tests)</h3>
 * Each thread acquires a unique user and releases it in {@code @AfterMethod}.
 * Guarantees one user = one browser at a time.
 *
 * <h3>Starvation detection (heartbeat-based)</h3>
 * A holder is "starving" only if it has not touched the browser (no Selenium command,
 * no wait, no page interaction) for at least the configured threshold. A long-but-
 * progressing test never trips the warning; a frozen page or stuck script trips it
 * immediately and the snapshot names the exact test method that has gone silent.
 *
 * <p>The heartbeat is fed by {@code HeartbeatListener} (a {@code WebDriverListener}
 * attached when a fresh WebDriver is stored in {@code DriverManager.setDriver}). Every
 * WebDriver/WebElement/Navigation/TargetLocator call updates {@link #LAST_ACTIVITY_MS}
 * for the current thread's user. Callers may also feed the heartbeat manually via
 * {@link #heartbeat(UserType, String)}.</p>
 *
 * <p>Two thresholds, both measured as <b>min(max-holder-idle, my-wait)</b> — i.e. you
 * only count time during which BOTH a holder has been silent AND the current thread is
 * queued for a user. A pool full of fresh holders is healthy by definition.</p>
 * <ul>
 *   <li><b>pool.warn.at.seconds</b> (default 300) — emits a WARN log and an ExtentReport
 *       infrastructure-node entry listing every active user with their {@code idle}
 *       (seconds since last browser call) and {@code held} (seconds since acquire)
 *       durations. Not a failure; purely for visibility.</li>
 *   <li><b>pool.acquire.timeout.seconds</b> (default 600) — throws
 *       {@link UserPoolAcquisitionTimeoutException} with a full active-user snapshot.</li>
 * </ul>
 *
 * <h3>SET-2 (concurrent / synchronized tests)</h3>
 * Users are grouped by {@code groupId}; threads in a group share a fixed user list
 * indexed by arrival order.  A {@code CyclicBarrier} in the test class synchronises
 * threads at checkpoints.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>One user = one active session — enforced by the semaphore + queue pair.</li>
 *   <li>A user type that was never configured causes an immediate {@code SkipException}
 *       rather than a silent hang.</li>
 *   <li>If {@code thread-count} exceeds the user count, extra threads block on acquire
 *       (not an error); a warning is logged at suite start by {@code SuiteDataLoader}.</li>
 * </ul>
 */
public final class UserPool {

    /**
     * Guards the queues below and the held-user bookkeeping. Fair, so threads waiting for a user are served in arrival order (the property the
     * old per-type fair semaphores gave). One lock for all types is what lets a {@code [UI, WEBAPI]} user, which sits in two queues, be handed out
     * once: taking it removes it from every queue it is in, in one critical section.
     */
    private static final ReentrantLock LOCK = new ReentrantLock(true);

    /** Signalled (per type) whenever a user of that type becomes free again. */
    private static final EnumMap<UserType, Condition> FREED = new EnumMap<>(UserType.class);

    /** Which type a held user was acquired as, by username (needed at release: a shared user was acquired as UI or as WEBAPI, not both). Guarded by {@link #LOCK}. */
    private static final Map<String, UserType> ACQUIRED_AS = new HashMap<>();

    /** Live pool — the FREE users of each type: removed (from every queue they are in) on acquire, put back on release. Guarded by {@link #LOCK}. */
    private static final EnumMap<UserType, LinkedList<UserCredential>> SET1_QUEUES =
            new EnumMap<>(UserType.class);

    /**
     * Initial count per type, set once during {@link #initialize}.
     * Used to distinguish "all users busy" (count > 0, queue empty) from
     * "no users configured" (count == 0), which need different error messages.
     */
    private static final EnumMap<UserType, Integer> INITIAL_COUNTS =
            new EnumMap<>(UserType.class);

    private static final ConcurrentHashMap<String, List<UserCredential>> SET2_GROUPS =
            new ConcurrentHashMap<>();

    /**
     * Tracks which test/thread currently holds each SET-1 user.
     * Populated on acquire, removed on release. Used to build starvation-warning snapshots.
     */
    private static final EnumMap<UserType, ConcurrentHashMap<String, ActiveUserEntry>> ACTIVE_USERS =
            new EnumMap<>(UserType.class);

    /**
     * Per-user heartbeat: epoch-ms of the last browser interaction by the holder.
     * Seeded on acquire, updated by {@link #heartbeat(UserType, String)} on every Selenium
     * command (wired from {@code HeartbeatListener}), removed on release. A holder whose
     * heartbeat has not advanced for {@code pool.warn.at.seconds} is considered silent and
     * surfaces in the infrastructure warning.
     */
    private static final EnumMap<UserType, ConcurrentHashMap<String, AtomicLong>> LAST_ACTIVITY_MS =
            new EnumMap<>(UserType.class);

    /**
     * Every configured SET-1 credential per type, in load order — retained purely for
     * reporting/introspection (e.g. the Extent report's "User Pool Status" table, see
     * {@code TestListener#onTestStart}). Distinct from {@link #SET1_QUEUES} (which only ever
     * holds the currently-FREE subset — removed on acquire, re-offered on release) and from
     * {@link #ACTIVE_USERS} (which only holds currently-busy holders, and goes stale under
     * {@code SessionPool}'s session-reuse model since it's only refreshed at session
     * *creation*, not on every reuse — see {@code SessionPool.currentTestCaseIdsByUsername()}
     * for the live, reuse-aware counterpart of "who's using this user right now").
     */
    private static final EnumMap<UserType, List<UserCredential>> ALL_SET1_USERS =
            new EnumMap<>(UserType.class);

    static {
        for (UserType type : UserType.values()) {
            SET1_QUEUES.put(type, new LinkedList<>());
            FREED.put(type, LOCK.newCondition());
            INITIAL_COUNTS.put(type, 0);
            ACTIVE_USERS.put(type, new ConcurrentHashMap<>());
            LAST_ACTIVITY_MS.put(type, new ConcurrentHashMap<>());
            ALL_SET1_USERS.put(type, List.of());
        }
    }

    private UserPool() {}

    // ─── Initialization ───────────────────────────────────────────────────────

    /**
     * (Re-)initializes the whole pool from a freshly-loaded credential list — clears and rebuilds
     * the SET1 queues/permits/roster and the SET2 groups from scratch. Idempotent and safe to call
     * more than once (e.g. suite re-runs); each call fully replaces the previous state rather than
     * merging into it. {@code SET1} credentials populate {@link #SET1_QUEUES}/{@link #SET1_PERMITS}
     * /{@link #ALL_SET1_USERS} per {@link UserType}; everything else is grouped by
     * {@link UserCredential#groupId()} into {@link #SET2_GROUPS}.
     */
    public static synchronized void initialize(List<UserCredential> credentials) {
        LOCK.lock();
        try {
            SET1_QUEUES.values().forEach(LinkedList::clear);
            ACQUIRED_AS.clear();
            SET2_GROUPS.clear();
            Map<UserType, List<UserCredential>> roster = new EnumMap<>(UserType.class);
            for (UserType type : UserType.values()) {
                INITIAL_COUNTS.put(type, 0);
                ACTIVE_USERS.get(type).clear();
                LAST_ACTIVITY_MS.get(type).clear();
                roster.put(type, new java.util.ArrayList<>());
            }

            int shared = 0;
            for (UserCredential cred : credentials) {
                if (cred.executionSet() == ExecutionSet.SET1) {
                    // A [UI, WEBAPI] user is in the queue and the count of BOTH types (one login, held for one use at a time).
                    for (UserType type : cred.types()) {
                        SET1_QUEUES.get(type).offer(cred);
                        INITIAL_COUNTS.merge(type, 1, Integer::sum);
                        roster.get(type).add(cred);
                    }
                    if (cred.isShared()) shared++;
                } else {
                    SET2_GROUPS
                            .computeIfAbsent(cred.groupId(), k -> new java.util.ArrayList<>())
                            .add(cred);
                }
            }
            roster.forEach((type, users) -> ALL_SET1_USERS.put(type, List.copyOf(users)));

            for (Map.Entry<UserType, Integer> e : INITIAL_COUNTS.entrySet()) {
                Log.info("[UserPool] Initialized — SET1 {}={} user(s)", e.getKey(), e.getValue());
            }
            if (shared > 0) {
                Log.info("[UserPool] {} user(s) are [UI, WEBAPI]: counted in both pools, held for one use at a time.", shared);
            }
            Log.info("[UserPool] Initialized — SET2 groups={}", SET2_GROUPS.keySet());
        } finally {
            LOCK.unlock();
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if at least one SET-1 user of the given type was loaded
     * during {@link #initialize}.  Use this before calling {@link #acquireSet1User}
     * for optional user types (e.g. API) to avoid a long blocking wait.
     */
    public static boolean hasUsersOfType(UserType type) {
        return INITIAL_COUNTS.getOrDefault(type, 0) > 0;
    }

    /**
     * Returns the initial (configured) count for a SET-1 user type.
     * Useful for suite-start validation against testng {@code thread-count}.
     */
    public static int initialCountOf(UserType type) {
        return INITIAL_COUNTS.getOrDefault(type, 0);
    }

    /**
     * Returns every configured SET-1 username of the given type, in load order — the full
     * roster a "used/unused" status table should enumerate (as opposed to {@link #SET1_QUEUES}'s
     * currently-free subset or {@link #ACTIVE_USERS}'s currently-busy subset). See
     * {@link #ALL_SET1_USERS}'s Javadoc for why neither of those alone is the right source.
     */
    public static List<String> allUsernamesOfType(UserType type) {
        List<UserCredential> creds = ALL_SET1_USERS.get(type);
        if (creds == null) return List.of();
        return creds.stream().map(UserCredential::username).toList();
    }

    // ─── Acquire / Release ────────────────────────────────────────────────────

    /**
     * Back-compat overload: acquires a user without a test label. New code should call
     * {@link #acquireSet1User(UserType, String)} with the test method name so the
     * starvation snapshot can identify the holder.
     */
    public static UserCredential acquireSet1User(UserType type) {
        return acquireSet1User(type, "<unknown>");
    }

    /**
     * Acquires the next available SET-1 user of the given type.
     *
     * <p>Uses a 500 ms poll loop so warn/timeout thresholds can be checked mid-wait
     * without a separate scheduled task. Stall is measured as
     * {@code min(max-holder-idle, my-wait)} — see class Javadoc.</p>
     *
     * @param type       the user type to acquire (UI/API)
     * @param testLabel  human-readable identifier of the test claiming the user
     *                   (typically the test method name). Surfaces in the starvation
     *                   snapshot.
     */
    public static UserCredential acquireSet1User(UserType type, String testLabel) {
        return acquireSet1UserInternal(type, testLabel, ConfigFactory.getExecutionConfig().poolAcquireTimeoutSeconds());
    }

    /** Shared implementation behind both {@code acquireSet1User} overloads — see that method's Javadoc for the poll/warn/timeout behavior; {@code timeoutSeconds} is broken out purely so callers/tests can override the configured default. */
    private static UserCredential acquireSet1UserInternal(UserType type, String testLabel, long timeoutSeconds) {
        int configured = INITIAL_COUNTS.getOrDefault(type, 0);

        // Immediate fast-fail — no users of this type were ever loaded
        if (configured == 0) {
            throw new org.testng.SkipException(
                    "[UserPool] No SET1 " + type + " users are configured in UsersList.xlsx. " +
                    "Add rows with usertype=[" + type + "], executionset=SET1.");
        }

        ExecutionConfig cfg  = ConfigFactory.getExecutionConfig();
        long warnAtMs        = cfg.poolWarnAtSeconds() * 1000L;
        long timeoutMs       = timeoutSeconds          * 1000L;
        long startMs         = System.currentTimeMillis();
        boolean warnSent     = false;
        String label         = (testLabel == null || testLabel.isBlank()) ? "<unknown>" : testLabel;

        while (true) {
            UserCredential cred = takeFree(type, label, 500);
            if (cred != null) {
                Log.info("[UserPool] [T-{}] Acquired SET1 {} user '{}' for '{}'.",
                        Thread.currentThread().getId(), type, cred.username(), label);
                ReportLogger.session("[T-{}] Acquired {} user '{}' for '{}'.",
                        Thread.currentThread().getId(), type, cred.username(), label);
                return cred;
            }

            // Heartbeat-based stall accounting: a holder is silent only if no browser
            // interaction has updated its heartbeat for `warnAtMs`. We take min(holderIdle,
            // myWaitMs) so a freshly-arrived waiter cannot inherit an old stall, and so
            // healthy queueing behind a fully-active pool never trips the warning.
            long now            = System.currentTimeMillis();
            long maxHolderIdle  = computeMaxHolderIdleMs(type, now);
            long myWaitMs       = now - startMs;
            long effectiveStall = Math.min(maxHolderIdle, myWaitMs);

            if (!warnSent && effectiveStall >= warnAtMs) {
                warnSent = true;
                String snapshot = buildActiveUserSnapshot(type, now);
                Log.warn("[UserPool] {} pool has a holder silent for {}s (thread '{}' waiting). Active users:\n{}",
                        type, effectiveStall / 1000, Thread.currentThread().getName(), snapshot);
                ExtentLogger.infoHtmlOnChildNode(
                        "Infrastructure Warnings",
                        "<b>[UserPool] " + type + " pool silent " + (effectiveStall / 1000)
                        + "s — thread '" + Thread.currentThread().getName() + "' waiting.</b><br>"
                        + snapshot.replace("\n", "<br>"));
            }

            // Hard timeout — genuine starvation: a holder has been silent for
            // poolAcquireTimeoutSeconds with at least one thread queued behind it.
            if (effectiveStall >= timeoutMs) {
                String snapshot = buildActiveUserSnapshot(type, now);
                // Log the snapshot explicitly and separately from the exception message — TestNG's
                // skip-handling does not reliably surface a multi-line exception message intact
                // into the Extent report (confirmed: the rendered skip entry showed only the
                // generic summary sentence, dropping this exact snapshot). Logging it here means
                // "who was stuck" is visible regardless of what happens to the exception's message
                // downstream.
                Log.error("[UserPool] {} pool TIMEOUT — holder silent for {}s while thread '{}' queued. Active users:\n{}",
                        type, timeoutSeconds, Thread.currentThread().getName(), snapshot);
                ReportLogger.sessionWarn("[UserPool TIMEOUT] {} pool — holder silent for {}s while thread '{}' was queued.",
                        type, timeoutSeconds, Thread.currentThread().getName());
                ExtentLogger.infoHtmlOnChildNode(
                        "Infrastructure Warnings",
                        "<b>[UserPool TIMEOUT] " + type + " pool — holder silent "
                        + timeoutSeconds + "s, thread '" + Thread.currentThread().getName()
                        + "' was queued and is now being skipped.</b><br>"
                        + snapshot.replace("\n", "<br>"));
                throw new UserPoolAcquisitionTimeoutException(
                        "[UserPool] " + type + " pool has a holder silent for "
                        + timeoutSeconds + "s while a thread is queued "
                        + "(a holder is likely stuck/hung).\n"
                        + "Active users at timeout:\n" + snapshot);
            }
        }
    }

    /**
     * Takes a free user of {@code type}, waiting up to {@code waitMs} for one to be released; {@code null} when none became free. A user that serves
     * only this type is preferred over a shared {@code [UI, WEBAPI]} one, so shared logins stay available for the other use. The user is removed from
     * EVERY queue it is in and recorded as held (as {@code type}) before the lock is released, so it cannot be handed out a second time.
     */
    private static UserCredential takeFree(UserType type, String label, long waitMs) {
        LOCK.lock();
        try {
            UserCredential cred = pickFree(type);
            if (cred == null) {
                FREED.get(type).await(waitMs, TimeUnit.MILLISECONDS);
                cred = pickFree(type);
            }
            if (cred == null) return null;
            for (UserType t : cred.types()) SET1_QUEUES.get(t).remove(cred);
            long now = System.currentTimeMillis();
            ACQUIRED_AS.put(cred.username(), type);
            ACTIVE_USERS.get(type).put(cred.username(), new ActiveUserEntry(label, Thread.currentThread().getName(), now));
            LAST_ACTIVITY_MS.get(type).put(cred.username(), new AtomicLong(now));
            return cred;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("[UserPool] Interrupted while waiting for a SET1 " + type + " user.", e);
        } finally {
            LOCK.unlock();
        }
    }

    /** The first free user of {@code type} that serves only that type, else the first free shared one; {@code null} when none is free. Caller holds {@link #LOCK}. */
    private static UserCredential pickFree(UserType type) {
        LinkedList<UserCredential> free = SET1_QUEUES.get(type);
        for (UserCredential c : free) {
            if (!c.isShared()) return c;
        }
        return free.peekFirst();
    }

    /**
     * Acquires the account for a WEBAPI session: a user listed for WEBAPI ({@code [WEBAPI]} or {@code [UI, WEBAPI]}) when the workbook has any, else - for
     * workbooks that have not been given the {@code usertype} column yet - a UI user, exactly as before (a warning is logged once per call).
     */
    public static UserCredential acquireForWebApi(String testLabel) {
        if (hasUsersOfType(UserType.WEBAPI)) return acquireSet1User(UserType.WEBAPI, testLabel);
        Log.warn("[UserPool] No WEBAPI users configured in UsersList.xlsx - borrowing a UI user for the WEBAPI session (legacy behaviour). "
                + "Mark the accounts to use as usertype=[UI, WEBAPI] (or [WEBAPI]).");
        return acquireSet1User(UserType.UI, testLabel);
    }

    /** Largest idle time (ms since last heartbeat) among {@code type}'s currently-active holders; {@code 0} if none. */
    private static long computeMaxHolderIdleMs(UserType type, long now) {
        ConcurrentHashMap<String, AtomicLong> heartbeats = LAST_ACTIVITY_MS.get(type);
        long maxIdle = 0;
        for (Map.Entry<String, AtomicLong> e : heartbeats.entrySet()) {
            long idle = now - e.getValue().get();
            if (idle > maxIdle) maxIdle = idle;
        }
        return maxIdle;
    }

    /** Renders one line per currently-active holder of {@code type} — username, holding test, thread, idle/held seconds, and a flag for duplicate thread names (a sign of overlapping TestNG pools) — for warn/timeout diagnostics. */
    private static String buildActiveUserSnapshot(UserType type, long now) {
        ConcurrentHashMap<String, ActiveUserEntry> map = ACTIVE_USERS.get(type);
        if (map == null || map.isEmpty()) return "  (none)";
        ConcurrentHashMap<String, AtomicLong> heartbeats = LAST_ACTIVITY_MS.get(type);

        // Identify thread names that appear more than once so we can flag them in the snapshot.
        Map<String, Integer> threadCounts = new HashMap<>();
        map.values().forEach(e -> threadCounts.merge(e.threadName(), 1, Integer::sum));
        Set<String> duplicatedThreads = new HashSet<>();
        threadCounts.forEach((tn, count) -> { if (count > 1) duplicatedThreads.add(tn); });

        StringBuilder sb = new StringBuilder();
        map.forEach((username, entry) -> {
            long held   = (now - entry.startMs()) / 1000;
            AtomicLong heartbeat = heartbeats.get(username);
            long idleMs = heartbeat == null ? (now - entry.startMs()) : (now - heartbeat.get());
            long idle   = idleMs / 1000;
            sb.append("  ").append(username)
              .append(" → ").append(entry.testName())
              .append(" (thread: ").append(entry.threadName())
              .append(", idle ").append(idle).append("s")
              .append(", held ").append(held).append("s)");
            if (duplicatedThreads.contains(entry.threadName())) {
                sb.append(" (duplicate thread name — different TestNG pool)");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    /**
     * Records a browser interaction for the given user. Called by {@code HeartbeatListener}
     * on every WebDriver/WebElement/Navigation/TargetLocator call. Lock-free and cheap —
     * a single {@code AtomicLong.set} per call. No-op if the user is not currently a
     * registered holder (e.g. released between two interleaved browser calls during
     * teardown).
     */
    public static void heartbeat(UserType type, String username) {
        if (username == null) return;
        ConcurrentHashMap<String, AtomicLong> map = LAST_ACTIVITY_MS.get(type);
        if (map == null) return;
        AtomicLong stamp = map.get(username);
        if (stamp != null) stamp.set(System.currentTimeMillis());
    }

    /**
     * Returns a SET-1 user back to the pool after the test method completes.
     * <b>Must</b> be called in {@code @AfterMethod} even when the test fails —
     * failure to do so will starve other threads.
     */
    public static void releaseSet1User(UserCredential cred) {
        if (cred == null) return;
        if (cred.executionSet() != ExecutionSet.SET1) return;

        ActiveUserEntry entry;
        UserType heldAs;
        LOCK.lock();
        try {
            heldAs = ACQUIRED_AS.remove(cred.username());
            if (heldAs == null) {
                // Not held (already released, or never acquired from the pool): putting it back would hand the same login out twice.
                Log.warn("[UserPool] Release of '{}' ignored - it is not currently held.", cred.username());
                return;
            }
            entry = ACTIVE_USERS.get(heldAs).remove(cred.username());
            LAST_ACTIVITY_MS.get(heldAs).remove(cred.username());
            for (UserType t : cred.types()) {
                SET1_QUEUES.get(t).offer(cred);
                FREED.get(t).signalAll(); // wakes the waiting threads of this type (arrival order)
            }
        } finally {
            LOCK.unlock();
        }

        long heldSeconds = entry != null ? (System.currentTimeMillis() - entry.startMs()) / 1000 : -1;
        String lastTest  = entry != null ? entry.testName() : "unknown";
        Log.info("[UserPool] [T-{}] RELEASED SET1 {} user '{}' — held {}s (was running '{}'). Pool now has a free user.",
                Thread.currentThread().getId(), heldAs, cred.username(), heldSeconds, lastTest);
        ReportLogger.session("[UserPool RELEASED] {} user '{}' — held {}s (was running '{}'). Pool now has a free user.",
                heldAs, cred.username(), heldSeconds, lastTest);
        if (entry != null && heldSeconds >= 0) {
            // lastTest is class-qualified ("ClassSimpleName.methodName" — see WebTest.setUp()),
            // letting the suite/class-level summary attribute pool-hold time by test class.
            com.gsl.reports.PoolAttributionStats.recordPoolHold(lastTest, heldSeconds);
        }
    }

    // ─── SET-2 ────────────────────────────────────────────────────────────────

    /**
     * Returns the full user list for a SET-2 group (immutable view after init).
     */
    public static List<UserCredential> getSet2Group(String groupId) {
        List<UserCredential> group = SET2_GROUPS.get(groupId);
        if (group == null) {
            throw new IllegalStateException(
                    "[UserPool] No SET2 group found for groupId='" + groupId +
                    "'. Check UsersList.xlsx groupId column.");
        }
        return group;
    }
}
