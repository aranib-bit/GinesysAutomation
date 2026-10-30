package com.gsl.utils.cheque;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.utils.Log;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks cheque leaves through the Finance API's cheque lookup — the {@code db.available=no} counterpart of the {@code finchqdet} reads in
 * {@link ChequeInstrumentResolver}. The lookup lists a bank GL's <em>usable</em> leaves, but keeps listing a leaf until some voucher holds
 * it, and the application treats a leaf held by ANY voucher — Unposted included — as "already in use". So every leaf handed out here is
 * claimed for the life of the JVM: two rows (or a source voucher and a later edit) never receive the same leaf.
 */
public final class ApiChequeResolver {

    private static final Set<String> CLAIMED = ConcurrentHashMap.newKeySet();

    private ApiChequeResolver() {}

    /** Claims and returns the first usable leaf of {@code bankGlCode} that is not claimed yet and not one of {@code exclude}. */
    public static Optional<ChequeLookupResult> claim(GeneralVoucherApiClient api, long bankGlCode, String workDate, Set<String> exclude) {
        List<ChequeLookupResult> all = api.fetchCheques(bankGlCode, workDate);
        if (all == null) return Optional.empty();
        for (ChequeLookupResult c : all) {
            if (c.getChequeNumber() == null || exclude.contains(c.getChequeNumber())) continue;
            if (c.getChequeStatus() != null && c.getChequeStatus().matches("(?i).*(used|cancel|stop|issued|clear|bounce).*")) continue;
            if (CLAIMED.add(bankGlCode + "|" + c.getChequeNumber())) return Optional.of(c);
        }
        return Optional.empty();
    }

    /**
     * Cheque shortfall backfill: when {@code bankGlCode} has no claimable leaf, registers a new cheque book through the Finance API
     * (HAR flow: duplicatecheques → create) and confirms a leaf is then claimable via the cheque lookup. No DB read.
     *
     * @return true when a claimable leaf exists afterwards (already existed, or the new book made one visible)
     */
    public static boolean ensureLeaves(GeneralVoucherApiClient api, long bankGlCode, long ownerSiteCode, long empCode, String workDate) {
        if (hasUnclaimed(api, bankGlCode, workDate)) return true;
        try {
            new com.gsl.api.service.ChequeBookService(api.chequeBooks()).createFreeChequeBook(bankGlCode, ownerSiteCode, empCode, workDate);
        } catch (RuntimeException e) {
            Log.warn("[ApiChequeResolver] Cheque book backfill failed for glcode={}: {}", bankGlCode, e.getMessage());
            return false;
        }
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            if (hasUnclaimed(api, bankGlCode, workDate)) return true;
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    /** True when at least one leaf of {@code bankGlCode} could still be claimed; claims nothing. */
    public static boolean hasUnclaimed(GeneralVoucherApiClient api, long bankGlCode, String workDate) {
        List<ChequeLookupResult> all = api.fetchCheques(bankGlCode, workDate);
        return all != null && all.stream().anyMatch(c -> c.getChequeNumber() != null && !CLAIMED.contains(bankGlCode + "|" + c.getChequeNumber()));
    }

    /**
     * Same contract as {@link ChequeInstrumentResolver#resolveKnownSameBank}: a registered, unused leaf of {@code thisVoucherGlcode}, or —
     * when none is left — the "unknown" fallback with a warning.
     */
    public static ChequeInstrumentResolver.ChequeResolution resolveKnownSameBank(GeneralVoucherApiClient api, long thisVoucherGlcode) {
        Optional<ChequeLookupResult> leaf = claim(api, thisVoucherGlcode, GeneralVoucherApiClient.workDate(LocalDate.now()), Set.of());
        if (leaf.isEmpty()) {
            Log.warn("[ApiChequeResolver] Known cheque requested but none available (unclaimed) for glcode={} — falling back to unknown.",
                    thisVoucherGlcode);
            return ChequeInstrumentResolver.resolveUnknown();
        }
        ChequeLookupResult c = leaf.get();
        return new ChequeInstrumentResolver.ChequeResolution(c.getChequeNumber(), ChequeInstrumentResolver.ChequeBankMatch.SAME_BANK,
                thisVoucherGlcode, c.getBookCode());
    }
}
