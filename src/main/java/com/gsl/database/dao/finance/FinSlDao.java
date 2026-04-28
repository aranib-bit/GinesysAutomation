package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinSl;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for finsl — Sub-Ledger master table.
 */
@RegisterBeanMapper(FinSl.class)
public interface FinSlDao {

    /**
     * Use when slid is provided in test data.
     */
    @SqlQuery("SELECT slcode, slid, slname, glcode FROM finsl WHERE slname = :slname AND slid = :slid")
    FinSl findBySlnameAndSlid(@Bind("slname") String slname, @Bind("slid") String slid);

    /**
     * Use when slid is not provided — expects a unique match on slname.
     */
    @SqlQuery("SELECT slcode, slid, slname, glcode FROM finsl WHERE slname = :slname")
    List<FinSl> findBySlname(@Bind("slname") String slname);
}
