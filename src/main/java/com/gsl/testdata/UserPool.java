package com.gsl.testdata;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ExecutionConfig;
import com.gsl.exceptions.UserPoolAcquisitionTimeoutException;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <h3>Starvation detection (stall-based, two-threshold strategy)</h3>
 * Both thresholds measure <b>how long the pool has gone without turning over</b> (no user
 * released), NOT how long an individual thread waited. A thread queued behind long-but-progressing
 * tests therefore never fails — every release resets the clock. Only a pool that has genuinely
 * stopped releasing (a stuck/hung holder) is treated as starvation.
 * <ul>
 *   <li><b>pool.warn.at.seconds</b> (default 300) — emits a WARN log and an ExtentReport
 *       infrastructure-node entry listing every active user and which thread holds them.
 *       Not a failure; purely for visibility when the pool stays idle longer than a normal test.</li>
 *   <li><b>pool.acquire.timeout.seconds</b> (default 600) — throws
 *       {@link UserPoolAcquisitionTimeoutException} with a full active-user snapshot when no user
 *       is released for this long. Bound this to the longest <em>single</em> test's hold time
 *       (≈2× is comfortable), NOT to total queue length. For soak/endurance runs use a separate
 *       config-soak.properties with a value of 14400 (4 hours) or higher.</li>
 * </ul>
 *
 * <p><b>Note:</b> primary concurrency control is TestNG's parallel data-provider pool
 * ({@code data-provider-thread-count} at the <em>suite</em> level == UI-user count): it admits
 * exactly that many rows and starts the next row's setUp only on a teardown. The semaphore below
 * then never blocks in normal operation; this stall detector is the deadlock safety net.</p>
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
     * Per-type timestamp of the last pool turnover (a user released back to the pool).
     * Starvation is measured as "how long the pool has gone WITHOUT turning over", not
     * "how long this thread personally waited". A thread queued behind long-but-progressing
     * tests must never fail — only a pool that has genuinely stopped releasing (a stuck/hung
     * holder) is starvation. Updated on every release and on {@link #initialize}.
     */
    private static final EnumMap<UserType, AtomicLong> LAST_PROGRESS_MS =
            new EnumMap<>(UserType.class);

    static {
        for (UserType type : UserType.values()) {
            SET1_QUEUES.put(type, new LinkedBlockingQueue<>());
            INITIAL_COUNTS.put(type, 0);
            ACTIVE_USERS.put(type, new ConcurrentHashMap<>());
            LAST_PROGRESS_MS.put(type, new AtomicLong(System.currentTimeMillis()));
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
            LAST_PROGRESS_MS.get(type).set(System.currentTimeMillis());
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
     * Acquires the next available SET-1 user of the given type.
     *
     * <p>Uses a 500 ms poll loop so warn/timeout thresholds can be checked mid-wait
     * without a separate scheduled task:</p>
     * <ul>
     *   <li>At {@code pool.warn.at.seconds}: one WARN log entry + ExtentReport
     *       infrastructure node entry listing all active holders. Not a failure.</li>
     *   <li>At {@code pool.acquire.timeout.seconds}: throws
     *       {@link UserPoolAcquisitionTimeoutException} with a full snapshot.</li>
     * </ul>
     *
     * <p>The only way a test is <em>skipped</em> is if zero users of this type were
     * configured in {@code UsersList.xlsx}.</p>
     */
    public static UserCredential acquireSet1User(UserType type) {
        int configured = INITIAL_COUNTS.getOrDefault(type, 0);

        // Immediate fast-fail — no users of this type were ever loaded
        if (configured == 0) {
            throw new org.testng.SkipException(
                    "[UserPool] No SET1 " + type + " users are configured in UsersList.xlsx. " +
                    "Add rows with userType=" + type + ", executionSet=SET1.");
        }

        ExecutionConfig cfg  = ConfigFactory.getExecutionConfig();
        long warnAtMs        = cfg.poolWarnAtSeconds()         * 1000L;
        long timeoutMs       = cfg.poolAcquireTimeoutSeconds() * 1000L;
        long startMs         = System.currentTimeMillis();
        boolean warnSent     = false;
        Semaphore sem        = SET1_PERMITS.get(type);

        while (true) {
            try {
                boolean acquired = sem.tryAcquire(500, TimeUnit.MILLISECONDS);
                if (acquired) {
                    UserCredential cred = SET1_QUEUES.get(type).poll();
                    ACTIVE_USERS.get(type).put(
                            cred.username(),
                            new ActiveUserEntry(
                                    Thread.currentThread().getName(),
                                    Thread.currentThread().getName(),
                                    System.currentTimeMillis()
                            )
                    );
                    Log.info("[UserPool] [T-{}] Acquired SET1 {} user '{}'.",
                            Thread.currentThread().getId(), type, cred.username());
                    return cred;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "[UserPool] Interrupted while waiting for a SET1 " + type + " user.", e);
            }

            // Stall-based accounting: measure how long the POOL has gone without turning over,
            // not how long this thread personally waited. max(startMs, lastProgress) prevents a
            // freshly-arrived waiter from inheriting an old stall. Each release resets lastProgress,
            // so a thread queued behind long-but-progressing tests never fails — only a pool that
            // has genuinely stopped releasing (a stuck/hung holder) trips the timeout.
            long now         = System.currentTimeMillis();
            long reference   = Math.max(startMs, LAST_PROGRESS_MS.get(type).get());
            long stalledFor  = now - reference;

            // Warn threshold — fires once per wait, not on every tick
            if (!warnSent && stalledFor >= warnAtMs) {
                warnSent = true;
                String snapshot = buildActiveUserSnapshot(type);
                Log.warn("[UserPool] {} pool has not turned over for {}s (thread '{}' waiting). Active users:\n{}",
                        type, stalledFor / 1000, Thread.currentThread().getName(), snapshot);
                ExtentLogger.infoHtmlOnChildNode(
                        "Infrastructure Warnings",
                        "<b>[UserPool] " + type + " pool idle " + (stalledFor / 1000)
                        + "s — thread '" + Thread.currentThread().getName() + "' waiting.</b><br>"
                        + snapshot.replace("\n", "<br>"));
            }

            // Hard timeout — genuine starvation: the pool has not released a single user for
            // poolAcquireTimeoutSeconds. This is bounded by the longest SINGLE test's hold time,
            // never by total queue length, so healthy queueing across many rows is safe.
            if (stalledFor >= timeoutMs) {
                String snapshot = buildActiveUserSnapshot(type);
                throw new UserPoolAcquisitionTimeoutException(
                        "[UserPool] " + type + " pool made no progress for "
                        + cfg.poolAcquireTimeoutSeconds() + "s — no user was released in that window "
                        + "(a holder is likely stuck/hung).\n"
                        + "Active users at timeout:\n" + snapshot);
            }
        }
    }

    private static String buildActiveUserSnapshot(UserType type) {
        ConcurrentHashMap<String, ActiveUserEntry> map = ACTIVE_USERS.get(type);
        if (map == null || map.isEmpty()) return "  (none)";
        StringBuilder sb = new StringBuilder();
        map.forEach((username, entry) -> {
            long runningFor = (System.currentTimeMillis() - entry.startMs()) / 1000;
            sb.append("  ").append(username)
              .append(" → thread: ").append(entry.threadName())
              .append(" (running ").append(runningFor).append("s)\n");
        });
        return sb.toString();
    }

    /**
     * Returns a SET-1 user back to the pool after the test method completes.
     * <b>Must</b> be called in {@code @AfterMethod} even when the test fails —
     * failure to do so will starve other threads.
     */
    public static void releaseSet1User(UserCredential cred) {
        if (cred == null) return;
        if (cred.executionSet() != ExecutionSet.SET1) return;

        ACTIVE_USERS.get(cred.userType()).remove(cred.username());
        SET1_QUEUES.get(cred.userType()).offer(cred);
        // Record pool turnover BEFORE releasing the permit so any waiter that wakes immediately
        // sees a fresh progress timestamp and resets its stall clock.
        LAST_PROGRESS_MS.get(cred.userType()).set(System.currentTimeMillis());
        SET1_PERMITS.get(cred.userType()).release(); // unblocks the next waiting thread (FIFO)
        Log.info("[UserPool] [T-{}] Released SET1 {} user '{}'.",
                Thread.currentThread().getId(), cred.userType(), cred.username());
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
