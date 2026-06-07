package com.gsl.database;

import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents concurrent test threads from over-reserving pending amounts for the same
 * (SubLedger, Reference Site) pair in Reference Site Wise FIFO Set-Off scenarios.
 *
 * <h3>Problem</h3>
 * Two parallel tests targeting SubLedger A / Site A with amounts X and F respectively
 * must satisfy X + F ≤ PendingZ. Without coordination both threads see the full
 * pending and independently claim it, causing the second test's Auto Adjust to fail.
 *
 * <h3>Solution</h3>
 * Atomic compare-and-set on a global {@code ConcurrentHashMap<key, reservedAmount>}.
 * {@link #tryReserve} succeeds only when {@code (current + requested) ≤ pending}.
 * {@link #releaseCurrentThread} is called in {@code BaseTest.tearDown()} to return
 * the reserved amount after the test finishes.
 */
public final class ReferenceSiteAmountRegistry {

    private static final ConcurrentHashMap<String, BigDecimal> RESERVED =
            new ConcurrentHashMap<>();

    private static final ThreadLocal<Map<String, BigDecimal>> THREAD_RESERVATIONS =
            ThreadLocal.withInitial(HashMap::new);

    private ReferenceSiteAmountRegistry() {}

    /**
     * Atomically reserves {@code amount} for the given (slName, slId, refSiteName) triple.
     *
     * @param slName       sub-ledger name
     * @param slId         sub-ledger ID
     * @param refSiteName  reference site name (ref_admsite_name from v_pending_adj)
     * @param amount       amount this test wants to claim
     * @param pendingAmount total pending for this (SL, refSite) from v_pending_adj
     * @return {@code true} if reservation succeeded; {@code false} if ceiling would be breached
     */
    public static boolean tryReserve(String slName, String slId, String refSiteName,
                                     BigDecimal amount, BigDecimal pendingAmount) {
        String key = slName + "|" + slId + "|" + refSiteName;
        boolean[] succeeded = {false};

        RESERVED.compute(key, (k, current) -> {
            BigDecimal curr = (current == null) ? BigDecimal.ZERO : current;
            if (curr.add(amount).compareTo(pendingAmount) <= 0) {
                succeeded[0] = true;
                return curr.add(amount);
            }
            return curr;
        });

        if (succeeded[0]) {
            THREAD_RESERVATIONS.get().merge(key, amount, BigDecimal::add);
            Log.debug("[RefSiteAmtReg] Thread '{}' reserved {} for key '{}'.",
                    Thread.currentThread().getName(), amount, key);
        } else {
            Log.warn("[RefSiteAmtReg] Ceiling breached for key '{}' — requested={}, pending={}.",
                    key, amount, pendingAmount);
        }
        return succeeded[0];
    }

    /**
     * Releases all amounts reserved by the current thread.
     * Call from {@code BaseTest.@AfterMethod}.
     */
    public static void releaseCurrentThread() {
        Map<String, BigDecimal> mine = THREAD_RESERVATIONS.get();
        if (!mine.isEmpty()) {
            mine.forEach((key, amount) ->
                RESERVED.computeIfPresent(key, (k, v) -> v.subtract(amount).max(BigDecimal.ZERO)));
            Log.debug("[RefSiteAmtReg] Thread '{}' released {} reservation(s): {}",
                    Thread.currentThread().getName(), mine.size(), mine.keySet());
            mine.clear();
        }
        THREAD_RESERVATIONS.remove();
    }

    /** Returns an unmodifiable snapshot of all currently reserved keys and amounts. */
    public static Map<String, BigDecimal> currentReservations() {
        return Collections.unmodifiableMap(RESERVED);
    }

    /** Clears all global reservations. Use only in test suite teardown. */
    public static void clearAll() {
        RESERVED.clear();
        THREAD_RESERVATIONS.remove();
    }
}
