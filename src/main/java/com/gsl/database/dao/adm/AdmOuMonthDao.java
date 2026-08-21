package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmOuMonth;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for admou_month — per-OU open/closed status for a single calendar month, split by
 * Finance/Inventory/COGS.
 */
@RegisterBeanMapper(AdmOuMonth.class)
public interface AdmOuMonthDao {

    @SqlQuery("SELECT code, admmonth_code, admou_code, cogs_run_status, inv_month_status, fin_month_status "
            + "FROM admou_month WHERE admmonth_code = :admmonthCode AND admou_code = :ouCode")
    Optional<AdmOuMonth> findByMonthAndOu(@Bind("admmonthCode") long admmonthCode, @Bind("ouCode") long ouCode);
}
