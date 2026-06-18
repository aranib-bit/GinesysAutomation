package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

/**
 * Static query helpers for the {@code admsite} table.
 *
 * <p><b>Schema facts (confirmed):</b>
 * <ul>
 *   <li>{@code admsite.slcode} — FK to {@code finsl.slcode}; NULL for company-default sites</li>
 *   <li>{@code admsite.code}   — PK; matched by {@code admcmp.default_admou_code}</li>
 *   <li>{@code admsite.name}   — human-readable site name shown in the UI Reference Site combo</li>
 * </ul>
 *
 * <p>Used by {@link ReferenceSiteQuery} to implement the 3-way reference site resolution logic.
 */
public class AdmSiteQuery {

    private AdmSiteQuery() {}

    /**
     * Counts the number of {@code admsite} rows associated with the given {@code slcode}.
     *
     * <p>Used to decide whether a Customer-type sub-ledger has exactly 1 site (use that site)
     * or multiple sites (fall back to the company-wide default site).
     *
     * @param jdbi   active JDBI instance
     * @param slcode the {@code finsl.slcode} value
     * @return count of admsite rows for this slcode
     */
    public static int countBySlcode(Jdbi jdbi, long slcode) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT COUNT(*) FROM admsite WHERE slcode = :slcode")
                .bind("slcode", slcode)
                .mapTo(Integer.class)
                .one()
        );
    }

    /**
     * Returns the {@code name} of the single {@code admsite} row for the given {@code slcode}.
     *
     * <p>Call only when {@link #countBySlcode} returns 1 — returns the first row if multiple
     * exist (defensive, should not happen in the single-site branch).
     *
     * @param jdbi   active JDBI instance
     * @param slcode the {@code finsl.slcode} value
     * @return the site name, or {@code null} if not found
     */
    public static String findNameBySlcode(Jdbi jdbi, long slcode) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT name FROM admsite WHERE slcode = :slcode LIMIT 1")
                .bind("slcode", slcode)
                .mapTo(String.class)
                .findFirst()
                .orElse(null)
        );
    }

    /**
     * Returns the {@code name} of the {@code admsite} row identified by its primary key {@code code}.
     *
     * <p>Used after {@link AdmCmpQuery#findDefaultAdmouCode} resolves the company-wide default
     * site code, to retrieve the corresponding name (e.g. "Hena Embroidery").
     *
     * @param jdbi active JDBI instance
     * @param code the {@code admsite.code} primary key (= {@code admcmp.default_admou_code})
     * @return the site name, or {@code null} if not found
     */
    public static String findNameByCode(Jdbi jdbi, long code) {
        return jdbi.withHandle(handle ->
            handle.createQuery("SELECT name FROM admsite WHERE code = :code")
                .bind("code", code)
                .mapTo(String.class)
                .findFirst()
                .orElse(null)
        );
    }
}
