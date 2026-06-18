package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

/**
 * Static query helpers for the {@code admcmp} (company configuration) table.
 *
 * <p><b>Schema facts (confirmed):</b>
 * <ul>
 *   <li>{@code admcmp.default_admou_code} — FK to {@code admsite.code}; points to the
 *       company-wide default operating unit / site used as the fallback Reference Site
 *       when a sub-ledger has no unique single site attached.</li>
 * </ul>
 *
 * <p>Used by {@link ReferenceSiteQuery} to resolve the environment-wide default site name.
 */
public class AdmCmpQuery {

    private AdmCmpQuery() {}

    /**
     * Returns the company-wide default OU code from {@code admcmp}.
     *
     * <p>The returned value maps to {@code admsite.code}, which in turn gives the default
     * Reference Site name (e.g. "Hena Embroidery") via {@link AdmSiteQuery#findNameByCode}.
     *
     * <p>There is exactly one row in {@code admcmp} per company configuration.
     *
     * @param jdbi active JDBI instance
     * @return the {@code default_admou_code} value
     */
    public static long findDefaultAdmouCode(Jdbi jdbi) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT default_admou_code FROM admcmp LIMIT 1")
                .mapTo(Long.class)
                .one()
        );
    }
}
