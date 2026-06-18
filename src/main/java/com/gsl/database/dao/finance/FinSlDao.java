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
    @SqlQuery("SELECT slcode, slid, slname, glcode, clscode, cmpcode, tds_app FROM finsl WHERE slname = :slname AND slid = :slid")
    FinSl findBySlnameAndSlid(@Bind("slname") String slname, @Bind("slid") String slid);

    /**
     * Use when slid is not provided — expects a unique match on slname.
     */
    @SqlQuery("SELECT slcode, slid, slname, glcode, clscode, cmpcode, tds_app FROM finsl WHERE slname = :slname")
    List<FinSl> findBySlname(@Bind("slname") String slname);

    /**
     * Reverse lookup — used by Edit/Delete DataSources to resolve slname + slid
     * from a finvchdet.slcode value.
     */
    @SqlQuery("SELECT slcode, slid, slname, glcode, clscode, cmpcode, tds_app FROM finsl WHERE slcode = :slcode")
    FinSl findBySlcode(@Bind("slcode") long slcode);

    /**
     * Returns all finsl rows matching the given tds_app value.
     * Use {@code "Y"} to find TDS-applicable vendor SLs, {@code "N"} for non-applicable.
     */
    @SqlQuery("SELECT slcode, slid, slname, glcode, clscode, cmpcode, tds_app FROM finsl WHERE tds_app = :tdsApplicable")
    List<FinSl> findByTdsApplicable(@Bind("tdsApplicable") String tdsApplicable);
}
