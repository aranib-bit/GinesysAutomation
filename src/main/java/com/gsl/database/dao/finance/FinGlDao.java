package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinGl;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for fingl — General Ledger master table (see {@link FinGl} for column meanings).
 */
@RegisterBeanMapper(FinGl.class)
public interface FinGlDao {

    /** Looks up the fingl row by its {@code glcode} primary key. */
    @SqlQuery("SELECT glcode, glname, srctype, slapp, costapp, type FROM fingl WHERE glcode = :glcode")
    FinGl findByGlCode(@Bind("glcode") long glcode);

    /** Looks up the fingl row by exact {@code glname} match. */
    @SqlQuery("SELECT glcode, glname, srctype, slapp, costapp, type FROM fingl WHERE glname = :glname")
    FinGl findByGlName(@Bind("glname") String glname);

    /**
     * Resolves general Expense/Income GLs (srctype = 'G') matching a specific
     * Sub-Ledger/Site applicability combination (see
     * com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType) for General
     * Voucher test-data generation.
     *
     * <p><b>Deliberately not scoped by admsite_code_owner</b> — confirmed live
     * that general Expense/Income GLs (srctype='G') never populate that column
     * (it's Cash/Bank-only: 0/165 vs 8/8 in a live sample). Per-GL site scoping
     * for Type2/Type3 lines instead comes from fingl_site/finsl_gl_site
     * (see FinGlSiteDao/FinSlGlSiteDao), not this table.
     *
     * @param slapp   "Y"/"N" — Sub Ledger Applicable
     * @param costapp "Y"/"N" — Site Applicable
     */
    @SqlQuery("SELECT glcode, glname, srctype, slapp, costapp, type FROM fingl " +
              "WHERE slapp = :slapp AND costapp = :costapp AND srctype = 'G' AND ext = 'N' " +
              "ORDER BY glcode")
    List<FinGl> findByApplicability(@Bind("slapp") String slapp, @Bind("costapp") String costapp);

    /**
     * Resolves general Expense/Income GLs (srctype = 'G') matching one exact
     * (accounting type, Sub-Ledger Applicable, Site Applicable) combination — the
     * query the General Voucher Expense/Income Detail combination matrix drives
     * per placeholder (see {@code GeneralVoucherCombinations}). Superset of
     * {@link #findByApplicability}, which existing single-phase callers may keep using.
     *
     * @param type    "A"/"L"/"I"/"E" — see {@link com.gsl.enums.GlAccountType}
     * @param slapp   "Y"/"N" — Sub Ledger Applicable
     * @param costapp "Y"/"N" — Site Applicable
     */
    @SqlQuery("SELECT glcode, glname, srctype, slapp, costapp, type FROM fingl " +
              "WHERE type = :type AND slapp = :slapp AND costapp = :costapp AND srctype = 'G' AND ext = 'N' " +
              "ORDER BY glcode")
    List<FinGl> findByTypeAndApplicability(@Bind("type") String type,
                                            @Bind("slapp") String slapp,
                                            @Bind("costapp") String costapp);
}
