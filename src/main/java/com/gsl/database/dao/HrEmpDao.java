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

    /**
     * Reverse lookup — resolves a known {@code ecode} back to its login {@code abbre}, for report
     * display (e.g. {@code SnapshotValidationReporter} showing "50997 (aranibarman)" instead of a
     * bare employee code). {@code abbre}, not a full name, per explicit instruction — no employee
     * "display name" column/model exists in this codebase. Empty if no employee row matches.
     */
    @SqlQuery("SELECT abbre FROM hrdemp WHERE ecode = :ecode")
    Optional<String> findAbbreByEcode(@Bind("ecode") long ecode);
}
