package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmMonth;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for admmonth — one row per calendar month within a fiscal year. Used by
 * {@code AccountingPeriodResolver#findContainingMonth} to scan a year's months (ordered
 * chronologically) for the one whose {@code [dtfr, dtto]} range contains a given date.
 */
@RegisterBeanMapper(AdmMonth.class)
public interface AdmMonthDao {

    /** Returns every {@link AdmMonth} row for the given fiscal year, ordered by start date. */
    @SqlQuery("SELECT mcode, monstdt, ycode, month_name, dtfr, dtto FROM admmonth "
            + "WHERE ycode = :ycode ORDER BY monstdt")
    List<AdmMonth> findByYcode(@Bind("ycode") long ycode);
}
