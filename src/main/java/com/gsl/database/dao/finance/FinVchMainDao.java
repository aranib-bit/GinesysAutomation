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

    @SqlQuery("SELECT COUNT(*) FROM finvchmain WHERE scheme_docno = :schemeDocno")
    int countBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    @SqlQuery("SELECT vchcode FROM finvchmain WHERE scheme_docno = :schemeDocno")
    long findVchcodeBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    @SqlQuery("SELECT *, admsite_code_owner AS siteid FROM finvchmain WHERE scheme_docno = :schemeDocno")
    Optional<FinVchMain> findBySchemeDocno(@Bind("schemeDocno") String schemeDocno);

    @SqlQuery("SELECT *, admsite_code_owner AS siteid FROM finvchmain WHERE vchcode = :vchcode")
    Optional<FinVchMain> findByVoucherCode(@Bind("vchcode") long vchcode);
}
