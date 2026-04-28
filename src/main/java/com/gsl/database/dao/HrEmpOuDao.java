package com.gsl.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for main.hrdemp_ou — employee OU/site assignments.
 * Dialect-neutral methods only; dialect-sensitive queries (those needing LIMIT 1)
 * are executed via withHandle in DbArVoucherDataSource.
 */
public interface HrEmpOuDao {

    /** Returns all OU codes assigned to the employee. */
    @SqlQuery("SELECT DISTINCT admou_code FROM hrdemp_ou WHERE hrdemp_code = :ecode")
    List<Long> findOuCodesByEcode(@Bind("ecode") long ecode);

    /** Returns all site codes assigned to the employee. */
    @SqlQuery("SELECT DISTINCT admsite_code FROM hrdemp_ou WHERE hrdemp_code = :ecode")
    List<Long> findSiteCodesByEcode(@Bind("ecode") long ecode);
}
