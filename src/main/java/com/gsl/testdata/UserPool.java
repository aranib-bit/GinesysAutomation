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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Live pool — users are removed on acquire and returned on release. */
    private static final EnumMap<UserType, LinkedBlockingQueue<UserCredential>> SET1_QUEUES =
            new EnumMap<>(UserType.class);

    /**
     * Fair semaphore per user type — ensures waiting threads are served FIFO.
     * {@code LinkedBlockingQueue.take()} uses a non-fair lock internally, so without
     * this gate a later thread can steal a user from an earlier one.
     * Initialised (or re-initialised) in {@link #initialize} once the user count is known.
     */
    private static final EnumMap<UserType, Semaphore> SET1_PERMITS =
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

    static {
        for (UserType type : UserType.values()) {
            SET1_QUEUES.put(type, new LinkedBlockingQueue<>());
            INITIAL_COUNTS.put(type, 0);
            ACTIVE_USERS.put(type, new ConcurrentHashMap<>());
            LAST_ACTIVITY_MS.put(type, new ConcurrentHashMap<>());
        }
    }

    private UserPool() {}

    // ─── Initialization ───────────────────────────────────────────────────────

    public static synchronized void initialize(List<UserCredential> credentials) {
        SET1_QUEUES.values().forEach(LinkedBlockingQueue::clear);
        SET2_GROUPS.clear();
        for (UserType type : UserType.values()) {
            INITIAL_COUNTS.put(type, 0);
            ACTIVE_USERS.get(type).clear();
            LAST_ACTIVITY_MS.get(type).clear();
        }

        for (UserCredential cred : credentials) {
            if (cred.executionSet() == ExecutionSet.SET1) {
                SET1_QUEUES.get(cred.userType()).offer(cred);
                INITIAL_COUNTS.merge(cred.userType(), 1, Integer::sum);
            } else {
                SET2_GROUPS
                        .computeIfAbsent(cred.groupId(), k -> new java.util.ArrayList<>())
                        .add(cred);
            }
        }

        for (Map.Entry<UserType, Integer> e : INITIAL_COUNTS.entrySet()) {
            Log.info("[UserPool] Initialized — SET1 {}={} user(s)", e.getKey(), e.getValue());
            // Fair semaphore: permits == user count; fair=true guarantees FIFO wake-up order
            SET1_PERMITS.put(e.getKey(), new Semaphore(e.getValue(), true));
        }
        Log.info("[UserPool] Initialized — SET2 groups={}", SET2_GROUPS.keySet());
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

    private static UserCredential acquireSet1UserInternal(UserType type, String testLabel, long timeoutSeconds) {
        int configured = INITIAL_COUNTS.getOrDefault(type, 0);

        // Immediate fast-fail — no users of this type were ever loaded
        if (configured == 0) {
            throw new org.testng.SkipException(
                    "[UserPool] No SET1 " + type + " users are configured in UsersList.xlsx. " +
                    "Add rows with userType=" + type + ", executionSet=SET1.");
        }

        ExecutionConfig cfg  = ConfigFactory.getExecutionConfig();
        long warnAtMs        = cfg.poolWarnAtSeconds() * 1000L;
        long timeoutMs       = timeoutSeconds          * 1000L;
        long startMs         = System.currentTimeMillis();
        boolean warnSent     = false;
        Semaphore sem        = SET1_PERMITS.get(type);
        String label         = (testLabel == null || testLabel.isBlank()) ? "<unknown>" : testLabel;

        while (true) {
            try {
                boolean acquired = sem.tryAcquire(500, TimeUnit.MILLISECONDS);
                if (acquired) {
                    UserCredential cred = SET1_QUEUES.get(type).poll();
                    long now = System.currentTimeMillis();
                    ACTIVE_USERS.get(type).put(
                            cred.username(),
                            new ActiveUserEntry(
                                    label,
                                    Thread.currentThread().getName(),
                                    now
                            )
                    );
                    LAST_ACTIVITY_MS.get(type).put(cred.username(), new AtomicLong(now));
                    Log.info("[UserPool] [T-{}] Acquired SET1 {} user '{}' for '{}'.",
                            Thread.currentThread().getId(), type, cred.username(), label);
                    ReportLogger.session("[T-{}] Acquired {} user '{}' for '{}'.",
                            Thread.currentThread().getId(), type, cred.username(), label);
                    return cred;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "[UserPool] Interrupted while waiting for a SET1 " + type + " user.", e);
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

    private static long computeMaxHolderIdleMs(UserType type, long now) {
        ConcurrentHashMap<String, AtomicLong> heartbeats = LAST_ACTIVITY_MS.get(type);
        long maxIdle = 0;
        for (Map.Entry<String, AtomicLong> e : heartbeats.entrySet()) {
            long idle = now - e.getValue().get();
            if (idle > maxIdle) maxIdle = idle;
        }
        return maxIdle;
    }

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

        ActiveUserEntry entry = ACTIVE_USERS.get(cred.userType()).remove(cred.username());
        LAST_ACTIVITY_MS.get(cred.userType()).remove(cred.username());
        SET1_QUEUES.get(cred.userType()).offer(cred);
        SET1_PERMITS.get(cred.userType()).release(); // unblocks the next waiting thread (FIFO)

        long heldSeconds = entry != null ? (System.currentTimeMillis() - entry.startMs()) / 1000 : -1;
        String lastTest  = entry != null ? entry.testName() : "unknown";
        Log.info("[UserPool] [T-{}] RELEASED SET1 {} user '{}' — held {}s (was running '{}'). Pool now has a free permit.",
                Thread.currentThread().getId(), cred.userType(), cred.username(), heldSeconds, lastTest);
        ReportLogger.session("[UserPool RELEASED] {} user '{}' — held {}s (was running '{}'). Pool now has a free permit.",
                cred.userType(), cred.username(), heldSeconds, lastTest);
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
