package com.gsl.database;

import com.gsl.utils.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents concurrent test threads from claiming the same pending document.
 *
 * <h3>Problem</h3>
 * When tests run in parallel, multiple threads call
 * {@code DbArVoucherDataSource.loadFreshForScenario()} simultaneously. Each thread
 * queries {@code v_pending_adj} and may see the same pending sub-ledger documents —
 * causing two tests to act on identical data.
 *
 * <h3>Solution</h3>
 * A two-level reservation structure:
 * <ul>
 *   <li>{@code RESERVED} — a global {@link ConcurrentHashMap}-backed set. A document
 *       is added atomically via {@link Set#add}; only the first thread to add it wins.
 *       Prevents any cross-thread duplication.</li>
 *   <li>{@code THREAD_RESERVATIONS} — a {@link ThreadLocal} that tracks which document
 *       numbers the <em>current</em> thread has reserved. Used by
 *       {@link #releaseCurrentThread()} in {@code BaseTest.tearDown()} so the cleanup
 *       code needs no knowledge of what was reserved.</li>
 * </ul>
 *
 * <h3>Usage in DataSource</h3>
 * <pre>
 *   // After fetching pendingDocsBySlName from DB, filter to un-reserved docs only:
 *   pendingDocsBySlName = DocumentReservationRegistry.filterAndReserve(pendingDocsBySlName);
 * </pre>
 *
 * <h3>Usage in BaseTest</h3>
 * <pre>
 *   {@literal @}AfterMethod
 *   public void tearDown() {
 *       DocumentReservationRegistry.releaseCurrentThread();
 *       Driver.quitDriver();
 *       TestContextManager.clear();
 *   }
 * </pre>
 */
public final class DocumentReservationRegistry {

    /** Global set — ConcurrentHashMap.newKeySet() gives a lock-free, thread-safe Set. */
    private static final Set<String> RESERVED = ConcurrentHashMap.newKeySet();

    /** Per-thread tracker — what THIS thread has reserved so far in the current test. */
    private static final ThreadLocal<Set<String>> THREAD_RESERVATIONS =
            ThreadLocal.withInitial(HashSet::new);

    private DocumentReservationRegistry() {}

    /**
     * Atomically reserves {@code documentNo} globally and records it in the current
     * thread's reservation set.
     *
     * @return {@code true}  if this thread successfully claimed the document;
     *         {@code false} if another thread already holds it
     */
    public static boolean tryReserve(String documentNo) {
        boolean claimed = RESERVED.add(documentNo);
        if (claimed) {
            THREAD_RESERVATIONS.get().add(documentNo);
            Log.debug("[DocReservation] Thread '{}' reserved document '{}'.",
                    Thread.currentThread().getName(), documentNo);
        } else {
            Log.debug("[DocReservation] Document '{}' already held by another thread — skipping.",
                    documentNo);
        }
        return claimed;
    }

    /**
     * Filters a map of {@code slKey → List<PendingSlDocument>} to keep only documents
     * that this thread can successfully reserve.  Documents already reserved by another
     * thread are silently removed from the returned map.
     *
     * <p>Entries whose document list becomes empty after filtering are also removed,
     * so callers can detect when a sub-ledger has no usable documents left.
     *
     * @param docsBySlKey map from SL composite key to list of pending documents
     * @return new map with only un-reserved documents; backed by a {@link java.util.LinkedHashMap}
     *         to preserve ordering
     */
    public static java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>>
    filterAndReserve(java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> docsBySlKey) {

        java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> result =
                new java.util.LinkedHashMap<>();

        for (java.util.Map.Entry<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> entry
                : docsBySlKey.entrySet()) {

            java.util.List<com.gsl.model.finance.PendingSlDocument> available =
                    entry.getValue().stream()
                         .filter(doc -> tryReserve(doc.getDocumentNo()))
                         .collect(java.util.stream.Collectors.toList());

            if (!available.isEmpty()) {
                result.put(entry.getKey(), available);
            } else {
                Log.warn("[DocReservation] SL key='{}' has no un-reserved documents left — " +
                        "all {} doc(s) are held by other threads.", entry.getKey(), entry.getValue().size());
            }
        }
        return result;
    }

    /**
     * As {@link #filterAndReserve}, but reserves at most {@code perSl} documents of each sub-ledger, walking its documents in the order given and
     * skipping the ones another thread holds. The documents are NOT cut to {@code perSl} before reserving: when the oldest ones are held elsewhere
     * the next ones are taken, so a sub-ledger only counts as exhausted when it really has fewer than {@code perSl} free documents. Nothing beyond
     * what the scenario needs is held, which keeps the other threads' pool of documents intact.
     */
    public static java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>>
    filterAndReserveUpTo(java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> docsBySlKey, int perSl) {
        return filterAndReserveUpTo(docsBySlKey, perSl, Integer.MAX_VALUE);
    }

    /**
     * As {@link #filterAndReserveUpTo(java.util.Map, int)}, but stops once {@code maxSls} sub-ledgers are held. Walking every candidate held
     * documents of sub-ledgers the scenario would then hand back (AP-P2 run 20261031_062600 showed ~72 documents held at once), which made
     * concurrent scenarios see "no free documents". Callers pass the sub-ledgers they need plus a little headroom for ones rejected afterwards.
     */
    public static java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>>
    filterAndReserveUpTo(java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> docsBySlKey, int perSl, int maxSls) {
        java.util.Map<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.List<com.gsl.model.finance.PendingSlDocument>> entry : docsBySlKey.entrySet()) {
            if (result.size() >= maxSls) break;
            java.util.List<com.gsl.model.finance.PendingSlDocument> taken = new java.util.ArrayList<>();
            for (com.gsl.model.finance.PendingSlDocument doc : entry.getValue()) {
                if (taken.size() >= perSl) break;
                if (tryReserve(doc.getDocumentNo())) taken.add(doc);
            }
            if (taken.size() >= perSl) {
                result.put(entry.getKey(), taken);
            } else {
                // Fewer free documents than the scenario needs: hold none of them, so a partial hold never starves another scenario.
                release(taken.stream().map(com.gsl.model.finance.PendingSlDocument::getDocumentNo).toList());
                Log.warn("[DocReservation] SL key='{}' has only {} free document(s) of {} needed ({} in total, the rest held by other threads).",
                        entry.getKey(), taken.size(), perSl, entry.getValue().size());
            }
        }
        return result;
    }

    /**
     * Reserves every document of one sub-ledger or none of them: a FIFO / reference-site-wise FIFO set-off lets the UI allocate across the
     * sub-ledger's whole pending list, so the scenario must hold all of it (a partial hold lets another thread consume a document the UI is about
     * to pick). Documents are claimed in document-number order so two threads contending for the same sub-ledger back off consistently.
     *
     * @return {@code true} when this thread now holds all {@code docs}; {@code false} (nothing held) when any was already held elsewhere
     */
    public static boolean reserveAllOrNone(String slKey, java.util.List<com.gsl.model.finance.PendingSlDocument> docs) {
        java.util.List<String> numbers = docs.stream().map(com.gsl.model.finance.PendingSlDocument::getDocumentNo).sorted().toList();
        java.util.List<String> got = new java.util.ArrayList<>();
        for (String number : numbers) {
            if (tryReserve(number)) {
                got.add(number);
            } else {
                release(got);
                Log.warn("[DocReservation] SL key='{}': exclusive claim refused, '{}' is held by another thread (released the {} already taken).",
                        slKey, number, got.size());
                return false;
            }
        }
        Log.info("[DocReservation] SL key='{}': exclusive claim of all {} document(s) by thread '{}'.", slKey, numbers.size(), Thread.currentThread().getName());
        return true;
    }

    /**
     * Releases all documents reserved by the current thread and clears its thread-local set.
     * Call from {@code BaseTest.@AfterMethod} so every test cleans up regardless of pass/fail.
     */
    public static void releaseCurrentThread() {
        Set<String> mine = THREAD_RESERVATIONS.get();
        if (!mine.isEmpty()) {
            RESERVED.removeAll(mine);
            Log.debug("[DocReservation] Thread '{}' released {} document(s): {}",
                    Thread.currentThread().getName(), mine.size(), mine);
            mine.clear();
        }
        THREAD_RESERVATIONS.remove(); // prevent ThreadLocal leak
    }

    /**
     * Releases a specific document number regardless of which thread reserved it.
     * Prefer {@link #releaseCurrentThread()} for normal teardown.
     */
    public static void release(String documentNo) {
        RESERVED.remove(documentNo);
        THREAD_RESERVATIONS.get().remove(documentNo);
    }

    /** Releases a collection of document numbers. */
    public static void release(Collection<String> documentNos) {
        documentNos.forEach(DocumentReservationRegistry::release);
    }

    /** Returns an unmodifiable snapshot of all currently reserved document numbers. */
    public static Set<String> currentReservations() {
        return Collections.unmodifiableSet(RESERVED);
    }

    /**
     * Returns the full reservation state as a formatted string — use this to push the
     * snapshot to any destination (framework.log, Extent Report, or both).
     *
     * <p>Output example:
     * <pre>
     * [DocReservation] SNAPSHOT — 3 document(s) globally reserved:
     *   [1] SIC/2526/00201
     *   [2] SIC/2526/00202
     *   [3] SIC/2526/00205
     * Current thread (Thread-2) holds 1 document(s): [SIC/2526/00201]
     * </pre>
     *
     * <h3>To write to framework.log only</h3>
     * <pre>
     *   Log.info(DocumentReservationRegistry.snapshotAsString());
     * </pre>
     *
     * <h3>To write to Extent Report only</h3>
     * <pre>
     *   ExtentLogger.info(DocumentReservationRegistry.snapshotAsString());
     * </pre>
     *
     * <h3>To write to both</h3>
     * <pre>
     *   String snap = DocumentReservationRegistry.snapshotAsString();
     *   Log.info(snap);
     *   ExtentLogger.info(snap);
     * </pre>
     */
    public static String snapshotAsString() {
        Set<String> all  = currentReservations();
        Set<String> mine = Collections.unmodifiableSet(THREAD_RESERVATIONS.get());

        if (all.isEmpty()) {
            return "[DocReservation] SNAPSHOT — no documents currently reserved.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[DocReservation] SNAPSHOT — ")
          .append(all.size())
          .append(" document(s) globally reserved:\n");

        int i = 1;
        for (String docNo : all) {
            sb.append("  [").append(i++).append("] ").append(docNo).append("\n");
        }
        sb.append("Current thread (")
          .append(Thread.currentThread().getName())
          .append(") holds ").append(mine.size())
          .append(" document(s): ").append(mine);

        return sb.toString();
    }

    /**
     * Convenience method — writes {@link #snapshotAsString()} to {@code framework.log}
     * at INFO level.  Output does NOT appear in the Extent Report.
     * To write to Extent Report, use {@code ExtentLogger.info(snapshotAsString())} instead.
     */
    public static void logSnapshot() {
        Log.info(snapshotAsString());
    }

    /** Clears all global reservations and the current thread's local set. */
    public static void clearAll() {
        RESERVED.clear();
        THREAD_RESERVATIONS.remove();
    }
}
