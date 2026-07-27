package com.gsl.database.dao.adm;

import com.gsl.model.adm.AdmYear;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for admyear — fiscal year calendar.
 */
@RegisterBeanMapper(AdmYear.class)
public interface AdmYearDao {

    @SqlQuery("SELECT ycode, yname, dtfr, dtto FROM admyear WHERE ycode = :ycode")
    Optional<AdmYear> findByYcode(@Bind("ycode") long ycode);
}
