package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinSlGlSite;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for finsl_gl_site — the per-(Sub Ledger, GL)-pair Site narrowing (see
 * {@link FinSlGlSite}).
 */
@RegisterBeanMapper(FinSlGlSite.class)
public interface FinSlGlSiteDao {

    /** Returns the Sites narrowed for the given {@code finsl_gl.code} (Sub Ledger, GL) pair. */
    @SqlQuery("SELECT code, finsl_gl_code, admsite_code, ext FROM finsl_gl_site WHERE finsl_gl_code = :finslGlCode")
    List<FinSlGlSite> findByFinslGlCode(@Bind("finslGlCode") long finslGlCode);
}
