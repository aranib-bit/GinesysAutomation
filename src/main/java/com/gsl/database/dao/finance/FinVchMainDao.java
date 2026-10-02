package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinVchMain;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for finvchmain — Finance module.
 * Follows InvGrpDao pattern: JDBI3 SqlObject with @RegisterBeanMapper.
 */
@RegisterBeanMapper(FinVchMain.class)
public interface FinVchMainDao {

    /** Returns the number of finvchmain rows with the given {@code scheme_docno} (voucher document number). */
    @SqlQuery("SELECT COUNT(*) FROM finvchmain WHERE scheme_docno = :schemeDocno")
    int countBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    /** Resolves the {@code vchcode} primary key for a voucher given its {@code scheme_docno}. */
    @SqlQuery("SELECT vchcode FROM finvchmain WHERE scheme_docno = :schemeDocno")
    long findVchcodeBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    /** Looks up the full finvchmain header row by its {@code scheme_docno} (voucher document number). */
    @SqlQuery("SELECT * FROM finvchmain WHERE scheme_docno = :schemeDocno")
    Optional<FinVchMain> findBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    /** Looks up the full finvchmain header row by its {@code vchcode} primary key. */
    @SqlQuery("SELECT * FROM finvchmain WHERE vchcode = :vchcode")
    Optional<FinVchMain> findByVoucherCode(@Bind("vchcode") long vchcode);

    /**
     * Returns the most recent NON-released {@code finvchmain} row of the given {@code vchtype}
     * (e.g. {@code VoucherType.GENERAL.code()} = {@code "G"}) — used by lightweight checks (e.g.
     * UI-contract validation) that just need SOME existing, currently-editable voucher to open for
     * Edit, not one matching any particular business shape.
     *
     * <p><b>Must exclude {@code release_status='P'} (Posted/Released)</b> — confirmed live (runs
     * 20261001_000650/_003820): double-click-edit on a Released voucher shows Ginesys's own
     * "This document is released. No modifications can be made to a released document." alert
     * instead of opening the Edit form, which a lightweight UI check (unlike
     * {@code EditGeneralVoucher}'s real flow) never un-posts first via Actions→Revert. Restricted
     * to {@code 'N'}/{@code 'U'} (Not-released/Un-posted) — the same "still editable" status set
     * the real Edit data sources claim from, just without their combination-matrix business-shape
     * matching.
     */
    @SqlQuery("SELECT * FROM finvchmain WHERE vchtype = :vchtype AND release_status IN ('N','U') "
            + "ORDER BY vchcode DESC LIMIT 1")
    Optional<FinVchMain> findMostRecentByVoucherType(@Bind("vchtype") String vchtype);
}
