package com.gsl.testdata;

import com.gsl.utils.Log;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Thread-safe user pool for distributing credentials across parallel test threads.
 *
 * <h3>SET-1 (independent tests)</h3>
 * Each thread acquires a unique user via a <em>blocking</em> {@code take()} and releases
 * it in {@code @AfterMethod}.  Guarantees one user = one browser at a time.  There is no
 * acquire timeout — a thread waits indefinitely until a user is free, so tests are never
 * skipped due to slowness.
 *
 * <h3>SET-2 (concurrent / synchronized tests)</h3>
 * Users are grouped by {@code groupId}; threads in a group share a fixed user list
 * indexed by arrival order.  A {@code CyclicBarrier} in the test class synchronises
 * threads at checkpoints.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>One user = one active session — enforced by the blocking queue.</li>
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

    static {
        for (UserType type : UserType.values()) {
            SET1_QUEUES.put(type, new LinkedBlockingQueue<>());
            INITIAL_COUNTS.put(type, 0);
        }
    }

    private UserPool() {}

    // ─── Initialization ───────────────────────────────────────────────────────

    public static synchronized void initialize(List<UserCredential> credentials) {
        SET1_QUEUES.values().forEach(LinkedBlockingQueue::clear);
        SET2_GROUPS.clear();
        for (UserType type : UserType.values()) INITIAL_COUNTS.put(type, 0);

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
     * Acquires the next available SET-1 user of the given type, blocking the calling
     * thread until one is free.
     *
     * <p><b>No timeout</b> — the thread waits indefinitely.  Tests are never skipped
     * because a legitimate test took longer than some configured threshold.  The only
     * way a test is skipped is if zero users of this type were configured in
     * {@code UsersList.xlsx}.</p>
     *
     * <p><b>Concurrency guarantee:</b> each user is held by at most one thread at a
     * time.  {@link #releaseSet1User} in {@code @AfterMethod} returns it to the queue.
     */
    public static UserCredential acquireSet1User(UserType type) {
        int configured = INITIAL_COUNTS.getOrDefault(type, 0);

        // Immediate fast-fail — no users of this type were ever loaded
        if (configured == 0) {
            throw new org.testng.SkipException(
                    "[UserPool] No SET1 " + type + " users are configured in UsersList.xlsx. " +
                    "Add rows with userType=" + type + ", executionSet=SET1.");
        }

        try {
            // Fair semaphore ensures longest-waiting thread is served first (FIFO)
            SET1_PERMITS.get(type).acquire();
            // Permit acquired → a user is guaranteed to be in the queue right now
            UserCredential cred = SET1_QUEUES.get(type).poll();
            Log.info("[UserPool] [T-{}] Acquired SET1 {} user '{}'.",
                    Thread.currentThread().getId(), type, cred.username());
            return cred;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "[UserPool] Interrupted while waiting for a SET1 " + type + " user.", e);
        }
    }

    /**
     * Returns a SET-1 user back to the pool after the test method completes.
     * <b>Must</b> be called in {@code @AfterMethod} even when the test fails —
     * failure to do so will starve other threads.
     */
    public static void releaseSet1User(UserCredential cred) {
        if (cred == null) return;
        if (cred.executionSet() != ExecutionSet.SET1) return;

        SET1_QUEUES.get(cred.userType()).offer(cred);
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
