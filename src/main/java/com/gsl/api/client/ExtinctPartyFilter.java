package com.gsl.api.client;

import com.gsl.utils.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * An extinct vendor must never be used for a transaction (the UI refuses it), and the DB path always filtered {@code finsl.ext='N'}. The
 * Finance sub-ledger lookup the API sources read does not carry that flag, so this filter reads the extinct vendors from the Vendor master
 * ({@code VendorMaster/GetVendors}, {@code isextinct=Yes}) and drops them from candidate lists. The set is cached per JVM for a few minutes
 * because every scenario selects once and the master changes rarely during a run.
 */
public final class ExtinctPartyFilter {

    private static final long TTL_MS = 5 * 60_000L;
    private static volatile Set<Long> cached;
    private static volatile long loadedAt;

    private ExtinctPartyFilter() {}

    /** The sub-ledger codes of every extinct vendor (cached). */
    public static synchronized Set<Long> extinctSlCodes(VendorApiClient vendors, int connectedSite, int ouCode, String workDate) {
        long now = System.currentTimeMillis();
        if (cached == null || now - loadedAt > TTL_MS) {
            Set<Long> set = new HashSet<>();
            for (VendorApiClient.Vendor v : vendors.fetchVendors(connectedSite, ouCode, workDate)) {
                if (v.extinct()) set.add(v.code());
            }
            cached = set;
            loadedAt = now;
            Log.info("[ExtinctPartyFilter] {} extinct vendor(s) in the vendor master.", set.size());
        }
        return cached;
    }

    /** Forget the cached set (after a vendor was made extinct or active on purpose). */
    public static synchronized void invalidate() {
        cached = null;
    }

    /** {@code items} without the extinct parties; the number removed is logged. */
    public static <T> List<T> excluding(List<T> items, ToLongFunction<T> slCode, Set<Long> extinct) {
        List<T> kept = items.stream().filter(i -> !extinct.contains(slCode.applyAsLong(i))).collect(Collectors.toList());
        if (kept.size() != items.size()) {
            Log.info("[ExtinctPartyFilter] excluded {} extinct party/parties from {} candidate(s).", items.size() - kept.size(), items.size());
        }
        return kept;
    }
}
