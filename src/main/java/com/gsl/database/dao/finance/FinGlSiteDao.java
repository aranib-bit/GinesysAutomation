package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinGlSite;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for fingl_site — the GL → allowed Sites allow-list (see {@link FinGlSite}).
 *
 * <p>Used to resolve valid Site picks for a Type 2 ({@code costapp='Y'},
 * no Sub Ledger) row, and as the Type 3 fallback when {@code finsl_gl_site}
 * has no rows for the chosen (Sub Ledger, GL) pair — see
 * {@link FinSlGlSiteDao}.
 */
@RegisterBeanMapper(FinGlSite.class)
public interface FinGlSiteDao {

    /** Returns the allow-listed Sites for the given GL. */
    @SqlQuery("SELECT glcode, admsite_code, code FROM fingl_site WHERE glcode = :glcode")
    List<FinGlSite> findByGlcode(@Bind("glcode") long glcode);
}
