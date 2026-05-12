package com.gsl.database;

import com.gsl.database.query.AdmClsQuery;
import com.gsl.database.query.PendingAdjQuery;
import com.gsl.enums.DocumentSelectionMode;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import com.gsl.utils.Log;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Atomic SL + document selection with over-fetch, retry, and invariant assertion.
 *
 * <p>Replaces the previous two-step fetch in {@code DbArVoucherDataSource} (Step 5
 * {@code findSlNamesWithPendingAR} → Step 6 {@code filterAndReserve}) which had a
 * read-modify-write race: SLs returned by Step 5 could be fully reserved by competing
 * threads before Step 6, leaving the fixture builder with an SL list that did not
 * match the reserved-document map.
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li><b>Atomic consistency.</b> Returns a self-consistent {@link DocumentSelectionResult}
 *       where {@code selectedSls.size() == docsBySlKey.size()} and every SL has at least
 *       one reserved document.</li>
 *   <li><b>Contention tolerance.</b> Over-fetches {@code subLedgerCount × overfetchMultiplier}
 *       candidate SLs and retries up to {@code retryMax} times with {@code retryBackoffMs}
 *       backoff when the reserved pool is still too small.</li>
 *   <li><b>No leak on surplus.</b> Documents reserved for SLs that are dropped to meet
 *       the requested {@code subLedgerCount} are immediately released back to the global
 *       registry — keeps the pool healthy under repeated contention.</li>
 *   <li><b>Fail-fast.</b> On exhausting all retries, throws {@link IllegalStateException}
 *       with a full {@link DocumentReservationRegistry#snapshotAsString()} plus the
 *       SQL inputs used.</li>
 * </ul>
 *
 * <p><b>Threading:</b> stateless — all per-thread facts live inside
 * {@code DocumentReservationRegistry.THREAD_RESERVATIONS}. Safe under both SET-1
 * ({@code WebTest}) and SET-2 ({@code BaseSetTwoTest}) execution models.
 */
public final class DocumentSelector {

    private DocumentSelector() {}

    /**
     * Performs one atomic SL+document selection pass, retrying on contention.
     *
     * @param req the immutable selection request
     * @return the reconciled {@code (slList, docMap)} pair
     * @throws IllegalStateException if the pool cannot satisfy {@code subLedgerCount} after all retries
     */
    public static DocumentSelectionResult selectAtomic(SelectorRequest req) {
        if (req.getSubLedgerCount() <= 0) {
            throw new IllegalArgumentException("[DocSelector] subLedgerCount must be > 0, got " + req.getSubLedgerCount());
        }
        if (req.getDocumentCount() <= 0) {
            throw new IllegalArgumentException("[DocSelector] documentCount must be > 0, got " + req.getDocumentCount());
        }

        // Derive retry bound from actual distinct SL count in DB — no static config needed.
        long distinctSlCount = fetchDistinctSlCount(req);
        int maxAttempts = (int) Math.max(1L, distinctSlCount);
        Log.info("[DocSelector] distinctSlCount={} → maxAttempts={}", distinctSlCount, maxAttempts);

        int totalOverfetched = 0;
        int lastCandidatesSize = 0;
        int lastReservedSize = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int overfetch = Math.max(req.getSubLedgerCount(),
                    req.getSubLedgerCount() * Math.max(1, req.getOverfetchMultiplier()));

            List<SlNameWithId> candidates = fetchCandidateSls(req, overfetch);
            lastCandidatesSize = candidates.size();
            totalOverfetched += candidates.size();

            if (candidates.isEmpty()) {
                Log.warn("[DocSelector] attempt {}/{} — no candidate SLs in DB (overfetch={})",
                        attempt + 1, maxAttempts, overfetch);
                if (attempt < maxAttempts - 1) sleepBackoff(req, attempt);
                continue;
            }

            Map<String, List<PendingSlDocument>> rawDocs =
                    PendingAdjQuery.findDocumentsForSlNames(
                            req.getJdbi(), req.getOuCodes(), req.getOwnerSiteCodes(),
                            candidates, req.getDocumentCount());

            Map<String, List<PendingSlDocument>> reserved =
                    DocumentReservationRegistry.filterAndReserve(rawDocs);
            lastReservedSize = reserved.size();

            Map<String, List<PendingSlDocument>> moded = applyMode(reserved, req.getMode());

            TrimResult trim = trimToRequestedSize(candidates, moded, req.getSubLedgerCount());

            Log.info("[DocSelector] attempt={}/{} candidates={} reservedSls={} modedSls={} kept={} releasedSurplus={}",
                    attempt + 1, maxAttempts, candidates.size(),
                    reserved.size(), moded.size(), trim.kept.size(), trim.releasedDocNos.size());

            if (trim.kept.size() < req.getSubLedgerCount()) {
                Log.warn("[DocSelector] attempt {}/{} short — got {} SL(s), need {}. Backing off {}ms.",
                        attempt + 1, maxAttempts, trim.kept.size(),
                        req.getSubLedgerCount(), req.getRetryBackoffMs());
                if (attempt < maxAttempts - 1) sleepBackoff(req, attempt);
                continue;
            }

            DocumentSelectionResult out = new DocumentSelectionResult(
                    trim.kept, trim.keptDocs, attempt, totalOverfetched);
            assertInvariants(out);
            return out;
        }

        throw failHard(req, lastCandidatesSize, lastReservedSize, maxAttempts);
    }

    /**
     * Routes to the correct COUNT(DISTINCT slcode) query based on the same filter
     * as {@link #fetchCandidateSls}, ensuring the retry bound matches the actual data pool.
     */
    private static long fetchDistinctSlCount(SelectorRequest req) {
        if (req.getClsCode() != null && req.getClsCode() > 0) {
            return AdmClsQuery.countDistinctSlCodesByClsCode(
                    req.getJdbi(), req.getOuCodes(), req.getOwnerSiteCodes(), req.getClsCode());
        }
        if (req.getCmpCode() != null) {
            return AdmClsQuery.countDistinctSlCodesByCmpCode(
                    req.getJdbi(), req.getOuCodes(), req.getCmpCode());
        }
        return PendingAdjQuery.countDistinctSlCodes(
                req.getJdbi(), req.getOuCodes(), req.getOwnerSiteCodes());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ───────────────────────────────────────────────────────────────────────────

    /** Routes SL discovery through the correct query based on the classification filter. */
    private static List<SlNameWithId> fetchCandidateSls(SelectorRequest req, int overfetch) {
        if (req.getClsCode() != null && req.getClsCode() > 0) {
            return AdmClsQuery.findSlNamesWithPendingARByClsCode(
                    req.getJdbi(), req.getOuCodes(), req.getOwnerSiteCodes(),
                    req.getClsCode(), overfetch);
        }
        if (req.getCmpCode() != null) {
            return AdmClsQuery.findSlNamesWithPendingARByCmpCode(
                    req.getJdbi(), req.getOuCodes(), req.getCmpCode(), overfetch);
        }
        return PendingAdjQuery.findSlNamesWithPendingAR(
                req.getJdbi(), req.getOuCodes(), req.getOwnerSiteCodes(), overfetch);
    }

    /**
     * Applies the per-SL selection mode.
     * <ul>
     *   <li>POSITIVE — keep docs with {@code pending > 0} (SQL already enforces this).</li>
     *   <li>MIXED    — keep all reserved docs unchanged.</li>
     *   <li>HYBRID   — POSITIVE first; if the positive subset is empty for an SL,
     *                  fall back to the full reserved list for that SL.
     *                  Never drops the SL itself.</li>
     * </ul>
     */
    private static Map<String, List<PendingSlDocument>> applyMode(
            Map<String, List<PendingSlDocument>> reserved, DocumentSelectionMode mode) {

        if (mode == null || mode == DocumentSelectionMode.MIXED) return reserved;

        Map<String, List<PendingSlDocument>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<PendingSlDocument>> entry : reserved.entrySet()) {
            List<PendingSlDocument> all = entry.getValue();
            List<PendingSlDocument> positive = all.stream()
                    .filter(d -> d.getPending() != null
                            && d.getPending().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            if (mode == DocumentSelectionMode.POSITIVE) {
                if (!positive.isEmpty()) out.put(entry.getKey(), positive);
            } else { // HYBRID
                if (!positive.isEmpty()) {
                    out.put(entry.getKey(), positive);
                } else {
                    Log.info("[DocSelector] HYBRID fallback — SL '{}' has no positive docs, using mixed pool ({} doc(s)).",
                            entry.getKey(), all.size());
                    out.put(entry.getKey(), all);
                }
            }
        }
        return out;
    }

    /**
     * Trims the moded map down to exactly {@code want} SLs, preserving the original
     * DB ordering of {@code candidates}. Documents belonging to SLs that get dropped
     * are released back to the global registry so they remain available for other threads.
     */
    private static TrimResult trimToRequestedSize(
            List<SlNameWithId> candidates,
            Map<String, List<PendingSlDocument>> moded,
            int want) {

        List<SlNameWithId> kept = new ArrayList<>(want);
        Map<String, List<PendingSlDocument>> keptDocs = new LinkedHashMap<>();
        List<String> releasedDocNos = new ArrayList<>();

        int slIndex = 0;
        for (SlNameWithId sl : candidates) {
            slIndex++;
            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            List<PendingSlDocument> docs = moded.get(key);
            if (docs == null || docs.isEmpty()) {
                Log.info("[DocSelector] SL #{} '{}' — skipped (no available docs after reservation/mode filter). Trying next SL of same type.",
                        slIndex, key);
                continue;
            }

            if (kept.size() < want) {
                Log.info("[DocSelector] SL #{} '{}' — selected ({} doc(s) available).",
                        slIndex, key, docs.size());
                kept.add(sl);
                keptDocs.put(key, docs);
            } else {
                Log.info("[DocSelector] SL #{} '{}' — surplus, releasing {} doc(s) back to pool.",
                        slIndex, key, docs.size());
                for (PendingSlDocument d : docs) {
                    if (d.getDocumentNo() != null) {
                        releasedDocNos.add(d.getDocumentNo());
                    }
                }
            }
        }

        if (!releasedDocNos.isEmpty()) {
            DocumentReservationRegistry.release(releasedDocNos);
        }

        return new TrimResult(kept, keptDocs, releasedDocNos);
    }

    /** Defensive post-condition check — must hold for every returned result. */
    private static void assertInvariants(DocumentSelectionResult r) {
        if (r.getDocsBySlKey().size() != r.getSelectedSls().size()) {
            throw new IllegalStateException(
                    "[DocSelector] Invariant broken — selectedSls.size=" + r.getSelectedSls().size() +
                            " but docsBySlKey.size=" + r.getDocsBySlKey().size());
        }
        for (SlNameWithId sl : r.getSelectedSls()) {
            String key = PendingAdjQuery.compositeKey(sl.getSlName(), sl.getSlId());
            List<PendingSlDocument> docs = r.getDocsBySlKey().get(key);
            if (docs == null || docs.isEmpty()) {
                throw new IllegalStateException(
                        "[DocSelector] Invariant broken — SL '" + key + "' has no reserved docs");
            }
        }
    }

    private static void sleepBackoff(SelectorRequest req, int attempt) {
        long ms = Math.max(0L, req.getRetryBackoffMs());
        if (ms == 0L) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Log.warn("[DocSelector] backoff interrupted after attempt {} — stopping retries early.",
                    attempt + 1);
        }
    }

    private static IllegalStateException failHard(SelectorRequest req,
                                                  int lastCandidatesSize,
                                                  int lastReservedSize,
                                                  int maxAttempts) {
        String snap = DocumentReservationRegistry.snapshotAsString();
        String msg = "[DocSelector] EXHAUSTED — could not select " + req.getSubLedgerCount() +
                " SL(s) after " + maxAttempts + " attempt(s) (bound = distinct slcode count in DB).\n" +
                "  Inputs: ouCodes=" + req.getOuCodes() +
                ", ownerSiteCodes=" + req.getOwnerSiteCodes() +
                ", clsCode=" + req.getClsCode() +
                ", cmpCode=" + req.getCmpCode() +
                ", documentCount=" + req.getDocumentCount() +
                ", overfetchMultiplier=" + req.getOverfetchMultiplier() +
                ", maxAttempts=" + maxAttempts +
                ", retryBackoffMs=" + req.getRetryBackoffMs() +
                ", mode=" + req.getMode() + "\n" +
                "  Last attempt: candidates=" + lastCandidatesSize +
                ", reservedSls=" + lastReservedSize + "\n" +
                "  Hint: increase doc.selector.overfetch.multiplier in config.properties, " +
                "or ensure sufficient pending AR documents exist in v_pending_adj.\n" +
                snap;
        Log.error(msg);
        return new IllegalStateException(msg);
    }

    /** Internal result of the trim phase. */
    private static final class TrimResult {
        final List<SlNameWithId> kept;
        final Map<String, List<PendingSlDocument>> keptDocs;
        final List<String> releasedDocNos;

        TrimResult(List<SlNameWithId> kept,
                   Map<String, List<PendingSlDocument>> keptDocs,
                   List<String> releasedDocNos) {
            this.kept = kept;
            this.keptDocs = keptDocs;
            this.releasedDocNos = releasedDocNos;
        }
    }
}
