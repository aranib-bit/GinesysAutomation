package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinSlGl;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for finsl_gl — the Sub Ledger → allowed GLs allow-list (see {@link FinSlGl}).
 *
 * <p>Used to resolve valid Sub Ledgers for a Type 3 ({@code slapp='Y' AND
 * costapp='Y'}) GL, and to resolve the specific (Sub Ledger, GL) pair's own
 * {@code code} for the further site-narrowing lookup in
 * {@link FinSlGlSiteDao}.
 */
@RegisterBeanMapper(FinSlGl.class)
public interface FinSlGlDao {

    @SqlQuery("SELECT code, slcode, glcode, ext FROM finsl_gl WHERE glcode = :glcode AND ext = 'N'")
    List<FinSlGl> findByGlcode(@Bind("glcode") long glcode);

    @SqlQuery("SELECT code, slcode, glcode, ext FROM finsl_gl WHERE glcode = :glcode AND slcode = :slcode")
    FinSlGl findByGlcodeAndSlcode(@Bind("glcode") long glcode, @Bind("slcode") long slcode);
}
