package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

/**
 * Static query helper for the {@code admou} (Organizational Unit) table — mirrors
 * {@link AdmSiteQuery}'s shape. Used purely for report display (resolving an
 * {@code admou_code} value into its human-readable name, e.g. "Hena Embroidery"/"Prawns
 * Corner"), not for any business-rule assertion.
 */
public class AdmOuQuery {

    private AdmOuQuery() {}

    /**
     * Returns the {@code name} of the {@code admou} row identified by its primary key {@code code}.
     *
     * @param jdbi active JDBI instance
     * @param code the {@code admou.code} primary key
     * @return the OU name, or {@code null} if not found
     */
    public static String findNameByCode(Jdbi jdbi, long code) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT name FROM admou WHERE code = :code")
                .bind("code", code)
                .mapTo(String.class)
                .findFirst()
                .orElse(null)
        );
    }
}
