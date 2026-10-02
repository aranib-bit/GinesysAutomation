package com.gsl.utils.cheque;

import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.finance.FinChqDetDao;
import com.gsl.model.finance.FinChqDet;
import com.gsl.utils.Log;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * Round 20 — resolves the actual {@code chqno} to use for a Cheque-payment scenario, once the
 * known/unknown (and, for AR, same-bank/different-bank) decision has ALREADY been made by the
 * caller's own Fixture Factory class ({@code GeneralVoucherFixtureFactory}/{@code
 * ApVoucherFixtureFactory}/{@code ArVoucherFixtureFactory}, via {@code
 * com.gsl.utils.WeightedSequenceBuilder}). This class is deliberately a PURE resolver — it holds
 * no randomness and makes no known-vs-unknown decision itself; it only turns an already-decided
 * outcome into a real DB value (or a synthetic one for "unknown").
 *
 * <p>A missing "known" row (none registered/unused for the requested GL) is a graceful, logged
 * fallback to unknown — never a failure — since the ratio this round maintains is a best-effort
 * test-data property, not a per-invocation guarantee once real data availability is involved.
 */
public final class ChequeInstrumentResolver {

    private ChequeInstrumentResolver() {}

    /** Which kind of match produced the resolved {@code chqno} — surfaced for report logging. */
    public enum ChequeBankMatch {
        /** Resolved from a real, unused cheque registered against this voucher's own GL. */
        SAME_BANK,
        /** AR-only: resolved from a real, unused cheque registered against a different GL's book. */
        DIFFERENT_BANK,
        /** A synthetic, never-registered cheque number — no DB match was requested or available. */
        UNKNOWN
    }

    /** @param sourceGlcode the GL the resolved cheque is actually registered against — {@code null} for UNKNOWN.
     *  @param bookcode the resolved cheque's own {@code finchqdet.bookcode} (which cheque BOOK it's
     *         from, {@code FinChqDet#getBookcode()}) — {@code null} for UNKNOWN. Distinct from
     *         {@code bankMatch}/{@code sourceGlcode}: two cheques can share the same GL/bank account
     *         (same {@code bankMatch}/{@code sourceGlcode}) yet come from different cheque books —
     *         a single bank account can have multiple books issued over time — so callers that need
     *         to know whether the specific BOOK changed (e.g. a Payment-Info-Edit's {@code
     *         finvchdet.bookcode} assertion) must compare this field directly, not infer it from
     *         {@code bankMatch}. */
    public record ChequeResolution(String chqno, ChequeBankMatch bankMatch, Long sourceGlcode, Long bookcode) {}

    /** The "unknown cheque" outcome — a synthetic, never-registered number. */
    public static ChequeResolution resolveUnknown() {
        return new ChequeResolution(UniqueChequeGenerator.generateUnique(), ChequeBankMatch.UNKNOWN, null, null);
    }

    /** The "known cheque, same bank" outcome — a real, currently-unused cheque number registered
     *  against THIS voucher's own selected Cash/Bank GL, reserved via
     *  {@link DocumentReservationRegistry} so a second concurrently-running thread scanning the
     *  same candidate list can't resolve the identical row (Round 15 — see this class's own
     *  {@code findFirstUnused} Javadoc for the confirmed-live collision this replaces). Falls back
     *  to {@link #resolveUnknown()} (logged) if no candidate is available, or every candidate is
     *  already reserved by another thread. */
    public static ChequeResolution resolveKnownSameBank(Jdbi jdbi, long thisVoucherGlcode) {
        List<FinChqDet> candidates = jdbi.onDemand(FinChqDetDao.class).findFirstUnused(thisVoucherGlcode);
        FinChqDet det = claimOneCandidate(candidates);
        if (det == null) {
            Log.warn("[ChequeInstrumentResolver] Known cheque requested but none available "
                    + "(unclaimed) for glcode={} — falling back to unknown.", thisVoucherGlcode);
            return resolveUnknown();
        }
        return new ChequeResolution(det.getChqno(), ChequeBankMatch.SAME_BANK, thisVoucherGlcode, det.getBookcode());
    }

    /** AR's "known cheque, different bank" sub-case only — deliberately resolves a real, unused,
     *  reserved (see {@link #resolveKnownSameBank}) cheque number registered against a DIFFERENT
     *  GL's cheque book than {@code thisVoucherGlcode}. Falls back to {@link #resolveUnknown()}
     *  (logged) if no candidate is available, or every candidate is already reserved. */
    public static ChequeResolution resolveKnownDifferentBank(Jdbi jdbi, long thisVoucherGlcode) {
        List<FinChqDet> candidates = jdbi.onDemand(FinChqDetDao.class).findFirstUnusedExcludingGlcode(thisVoucherGlcode);
        FinChqDet det = claimOneCandidate(candidates);
        if (det == null) {
            Log.warn("[ChequeInstrumentResolver] Different-bank known cheque requested but none "
                    + "available (unclaimed, excluding glcode={}) — falling back to unknown.", thisVoucherGlcode);
            return resolveUnknown();
        }
        return new ChequeResolution(det.getChqno(), ChequeBankMatch.DIFFERENT_BANK, det.getGlcode(), det.getBookcode());
    }

    /**
     * Scans {@code candidates} in their already-fetched order, reserving the first one not already
     * held by another thread — the same claim-scan-with-reservation shape every {@code vchcode}
     * candidate list in this codebase already uses (e.g. {@code DbGeneralVoucherDeleteDataSource
     * #scanAndClaim}). Reserved via {@link DocumentReservationRegistry#tryReserve}, keyed on
     * {@code glcode:chqno} (not bare {@code chqno} alone — a cheque number is only unique WITHIN
     * one GL's own book, so two different GLs could otherwise collide on the same reservation key).
     * Released automatically at test teardown ({@code WebTest}'s {@code @AfterMethod}, same as
     * every other {@code DocumentReservationRegistry} reservation) — held for exactly as long as
     * the resolving test itself, which is the correct scope: nothing else should reuse this chqno
     * while a test that might still Save it is in flight.
     */
    private static FinChqDet claimOneCandidate(List<FinChqDet> candidates) {
        for (FinChqDet det : candidates) {
            String key = "CHQ:" + det.getGlcode() + ":" + det.getChqno();
            if (DocumentReservationRegistry.tryReserve(key)) {
                return det;
            }
        }
        return null;
    }
}
