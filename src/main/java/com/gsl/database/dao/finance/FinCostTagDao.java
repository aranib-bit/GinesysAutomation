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

    @SqlQuery("SELECT * FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode ORDER BY code")
    List<FinCostTag> findByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);

    @SqlQuery("SELECT * FROM fincosttag WHERE postcode = :postcode ORDER BY code")
    List<FinCostTag> findByPostcode(@Bind("postcode") long postcode);

    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode")
    BigDecimal sumDebitByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);

    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM fincosttag WHERE entcode = :entcode AND glcode = :glcode")
    BigDecimal sumCreditByEntcodeAndGlcode(@Bind("entcode") String entcode, @Bind("glcode") long glcode);
}
