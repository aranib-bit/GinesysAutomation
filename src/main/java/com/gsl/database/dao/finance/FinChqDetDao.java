package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinChqDet;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for finchqdet — Cheque Book Detail. New in Round 7 (see
 * {@code GeneralVoucherCreateChangeSets}/{@code GeneralVoucherDbValidator}'s cheque-book
 * sub-system design) — no Java layer existed for this table before.
 */
@RegisterBeanMapper(FinChqDet.class)
public interface FinChqDetDao {

    /** Resolves the cheque-book row for a specific cheque number on a specific GL's book —
     *  used to confirm a "known" cheque's BEFORE ({@code stat='N'}) / AFTER ({@code stat='I'})
     *  transition and to recover its {@code bookcode}. */
    @SqlQuery("SELECT * FROM finchqdet WHERE chqno = :chqno AND glcode = :glcode")
    FinChqDet findByChqnoAndGlcode(@Bind("chqno") String chqno, @Bind("glcode") long glcode);

    /**
     * Round 8 Part A — resolves every real, currently-unused ({@code stat='N'}) cheque number
     * registered in the given GL's cheque book, for the "known cheque number" test variant.
     * Returns an empty list when none exist (callers should skip the scenario rather than fail,
     * mirroring {@code findFirstUsableOwnerSite}/{@code pickGlByType}'s own empty-result handling
     * in {@code DbGeneralVoucherDataSource}/{@code DbApVoucherDataSource}).
     *
     * <p><b>Round 15 — no longer {@code LIMIT 1}.</b> A single deterministic winner meant two
     * concurrently-running threads resolving a known cheque for the SAME glcode always got the
     * identical row (same WHERE clause, same {@code ORDER BY}) — confirmed live (run
     * {@code 20260925_234705}): both threads passed a real, actually-registered cheque number
     * through to Save, and the second one hit the app's own "This cheque no is already in use"
     * rejection. Callers ({@code ChequeInstrumentResolver}) now scan this full candidate list and
     * reserve one via {@code DocumentReservationRegistry} before returning it — the same
     * claim-scan pattern every {@code vchcode} candidate list in this codebase already uses.
     */
    @SqlQuery("SELECT * FROM finchqdet WHERE glcode = :glcode AND stat = 'N' ORDER BY chqno")
    List<FinChqDet> findFirstUnused(@Bind("glcode") long glcode);

    /**
     * Round 20 — AR's "different bank" known-cheque sub-case: resolves every real, currently-unused
     * cheque number registered against a DIFFERENT GL's cheque book than {@code excludeGlcode}
     * (this voucher's own selected Cash/Bank GL) — deliberately cross-bank, to test whether the
     * app correctly treats a cheque registered to the wrong bank as unrecognized. Returns an empty
     * list when none exist, same empty-result contract as {@link #findFirstUnused}.
     *
     * <p>Round 15 — no longer {@code LIMIT 1}, same reasoning as {@link #findFirstUnused}.
     */
    @SqlQuery("SELECT * FROM finchqdet WHERE glcode != :excludeGlcode AND stat = 'N' ORDER BY chqno")
    List<FinChqDet> findFirstUnusedExcludingGlcode(@Bind("excludeGlcode") long excludeGlcode);
}
