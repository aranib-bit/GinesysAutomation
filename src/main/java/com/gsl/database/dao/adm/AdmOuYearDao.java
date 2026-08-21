package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmOuYear;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for admou_year — per-OU open/closed status for a fiscal year.
 */
@RegisterBeanMapper(AdmOuYear.class)
public interface AdmOuYearDao {

    @SqlQuery("SELECT code, admyear_code, admou_code, admyear_status, admyear_closed_on, admyear_closed_by "
            + "FROM admou_year WHERE admyear_code = :ycode AND admou_code = :ouCode")
    Optional<AdmOuYear> findByYcodeAndOu(@Bind("ycode") long ycode, @Bind("ouCode") long ouCode);
}
