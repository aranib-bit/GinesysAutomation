package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinGl;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * DAO for fingl — General Ledger master table.
 */
@RegisterBeanMapper(FinGl.class)
public interface FinGlDao {

    @SqlQuery("SELECT glcode, glname, srctype FROM fingl WHERE glcode = :glcode")
    FinGl findByGlCode(@Bind("glcode") long glcode);

    @SqlQuery("SELECT glcode, glname, srctype FROM fingl WHERE glname = :glname")
    FinGl findByGlName(@Bind("glname") String glname);
}
