package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinCostTag;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO for fincosttag — the site-amount-allocation table (see {@link FinCostTag}).
 *
 * <p>{@code entcode} is a varchar column (text of finvchmain.vchcode) — every
 * query below accepts it as a String to match the real column type and avoid
 * a Postgres "bigint = character varying" operator error.
 */
@RegisterBeanMapper(FinCostTag.class)
public interface FinCostTagDao {

    /** Returns all fincosttag (site-amount-allocation) rows for the given voucher line ({@code entcode}, {@code glcode}), ordered by {@code code}. */
    @SqlQuery("SELECT * FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode ORDER BY code")
    List<FinCostTag> findByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);

    /** Returns all fincosttag rows for the given {@code postcode}, ordered by {@code code}. */
    @SqlQuery("SELECT * FROM fincosttag WHERE postcode = :postcode ORDER BY code")
    List<FinCostTag> findByPostcode(@Bind("postcode") long postcode);

    /** Sums {@code damount} across all fincosttag rows for the given voucher line; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode")
    BigDecimal sumDebitByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);

    /** Sums {@code camount} across all fincosttag rows for the given voucher line; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode")
    BigDecimal sumCreditByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);
}
