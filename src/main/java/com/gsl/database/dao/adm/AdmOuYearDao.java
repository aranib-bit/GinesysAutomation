package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmOuYear;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for admou_year — per-OU open/closed status for a fiscal year. Used by
 * {@code AccountingPeriodResolver#isYearOpen} to decide whether a voucher's edit date falls in a
 * year that's actually open for that OU, before checking the finer-grained month status
 * ({@link AdmOuMonthDao}).
 */
@RegisterBeanMapper(AdmOuYear.class)
public interface AdmOuYearDao {

    /** Returns the {@link AdmOuYear} row for the given fiscal year and OU, if one exists — its
     *  {@code admyearStatus} tells the caller whether that year is open ({@code "O"}) or closed
     *  ({@code "C"}) for that OU. */
    @SqlQuery("SELECT code, admyear_code, admou_code, admyear_status, admyear_closed_on, admyear_closed_by "
            + "FROM admou_year WHERE admyear_code = :ycode AND admou_code = :ouCode")
    Optional<AdmOuYear> findByYcodeAndOu(@Bind("ycode") long ycode, @Bind("ouCode") long ouCode);
}
