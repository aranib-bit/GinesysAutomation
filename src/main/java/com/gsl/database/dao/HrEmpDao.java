package com.gsl.database.dao;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for main.hrdemp — employee master.
 * Resolves the application login username (abbre) to the internal employee code (ecode).
 */
public interface HrEmpDao {

    /**
     * Returns the ecode for the given login abbreviation.
     * Example: abbre="aranibarman" → ecode=50997
     */
    @SqlQuery("SELECT ecode FROM hrdemp WHERE abbre = :abbre")
    Optional<Long> findEcodeByAbbre(@Bind("abbre") String abbre);
}
