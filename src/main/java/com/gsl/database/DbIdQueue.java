package com.gsl.database;

import com.gsl.utils.Log;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe registry that distributes DB test-data IDs to parallel test threads.
 *
 * <p>Usage pattern:
 * <ol>
 *   <li>{@link #register} is called once per key from {@code SuiteDataLoader.onStart()} —
 *       before any test thread starts.</li>
 *   <li>{@link #getNextId} is called from each {@code @DataSupplier} or test method — returns
 *       a unique ID per call via {@link ConcurrentLinkedQueue#poll()}, which is atomic
 *       and lock-free.</li>
 * </ol>
 *
 * <p>Keys follow the {@code "VOUCHER_TYPE.OPERATION"} convention defined in
 * {@link DbIdQueueKeys} (e.g., {@code "AR.CREATE"}, {@code "AP.EDIT"}).
 */
public final class DbIdQueue {

    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> QUEUES =
            new ConcurrentHashMap<>();

    private DbIdQueue() {}

    /**
     * Loads IDs for {@code key} from the database using {@code sql} and stores them in
     * a queue.  Must be called before parallel test threads start (e.g., from
     * {@code ISuiteListener.onStart}).
     *
     * @param key  queue key, e.g. {@code "AR.CREATE"}
     * @param sql  query that returns a single {@code Long} column (the ID)
     */
    public static void register(String key, String sql) {
        List<Long> ids = DatabaseManager.getJdbi().withHandle(handle ->
                handle.createQuery(sql)
                      .mapTo(Long.class)
                      .list()
        );
        QUEUES.put(key, new ConcurrentLinkedQueue<>(ids));
        Log.info("[DbIdQueue] Registered key='{}' with {} ID(s).", key, ids.size());
    }

    /**
     * Returns the next unclaimed ID for {@code key}, or {@code null} if the queue is
     * exhausted.  The {@link ConcurrentLinkedQueue#poll()} call is atomic — two threads
     * can never receive the same ID.
     *
     * @param key queue key, e.g. {@code "AR.CREATE"}
     * @return next ID, or {@code null} when no data is left
     */
    public static Long getNextId(String key) {
        ConcurrentLinkedQueue<Long> queue = QUEUES.get(key);
        if (queue == null) {
            Log.warn("[DbIdQueue] No queue registered for key='{}'. Was SuiteDataLoader run?", key);
            return null;
        }
        Long id = queue.poll();
        if (id == null) {
            Log.warn("[DbIdQueue] Queue for key='{}' is exhausted — no more test data.", key);
        }
        return id;
    }

    /** Returns the number of remaining IDs in the queue for {@code key}. */
    public static int remaining(String key) {
        ConcurrentLinkedQueue<Long> queue = QUEUES.get(key);
        return (queue != null) ? queue.size() : 0;
    }

    /** Clears all queues — useful for resetting state between suite runs in the same JVM. */
    public static void clearAll() {
        QUEUES.clear();
    }
}
