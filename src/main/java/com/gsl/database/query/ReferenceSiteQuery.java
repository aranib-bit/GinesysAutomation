package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

/**
 * Orchestration layer for resolving the AP Voucher Reference Site name per sub-ledger.
 *
 * <p>Implements the 3-way logic that mirrors the UI's auto-population behaviour:
 * <ol>
 *   <li>Customer-type SL ({@code admcls.clstype = 'C'}) with exactly 1 {@code admsite} row
 *       for its {@code slcode} → use that site's name.</li>
 *   <li>Customer-type SL with more than 1 {@code admsite} row → use the company-wide default
 *       site (from {@code admcmp.default_admou_code} → {@code admsite.name}).</li>
 *   <li>Any non-Customer SL → company-wide default site (same as case 2).</li>
 * </ol>
 *
 * <p>No SQL is executed here — this class delegates entirely to:
 * <ul>
 *   <li>{@link AdmCmpQuery}  — queries {@code admcmp}</li>
 *   <li>{@link AdmSiteQuery} — queries {@code admsite}</li>
 * </ul>
 *
 * <p><b>Performance note:</b> Call {@link #resolveDefaultSiteName} once per
 * {@code loadFreshForScenario} invocation and pass the result to
 * {@link #resolveForSl(Jdbi, long, String, String)} for each SL. This avoids
 * repeated {@code admcmp + admsite} round-trips across multiple SLs in the same scenario.
 */
public class ReferenceSiteQuery {

    private ReferenceSiteQuery() {}

    /**
     * Resolves the environment-wide default reference site name.
     *
     * <p>Chain: {@code admcmp.default_admou_code} → {@code admsite.name}
     *
     * <p>Example result: "Hena Embroidery" (the site whose {@code admsite.code}
     * equals {@code admcmp.default_admou_code}).
     *
     * @param jdbi active JDBI instance
     * @return default site name, or {@code null} if no admcmp row exists
     */
    public static String resolveDefaultSiteName(Jdbi jdbi) {
        long defaultCode = AdmCmpQuery.findDefaultAdmouCode(jdbi);
        return AdmSiteQuery.findNameByCode(jdbi, defaultCode);
    }

    /**
     * Resolves the reference site name for one sub-ledger using the 3-way logic.
     *
     * <p>Pass {@code defaultSiteName} from a single prior call to
     * {@link #resolveDefaultSiteName} — do not call {@code resolveDefaultSiteName}
     * inside a per-SL loop.
     *
     * @param jdbi            active JDBI instance
     * @param slcode          the {@code finsl.slcode} for this sub-ledger
     * @param clstype         the {@code admcls.clstype} for this sub-ledger's classification
     *                        (e.g. "C" for Customer, "S" for Supplier, etc.)
     * @param defaultSiteName pre-resolved company-wide default site name (from
     *                        {@link #resolveDefaultSiteName}); used for cases 2 and 3
     * @return the resolved reference site name
     */
    public static String resolveForSl(Jdbi jdbi, long slcode, String clstype, String defaultSiteName) {
        if ("C".equals(clstype)) {
            int count = AdmSiteQuery.countBySlcode(jdbi, slcode);
            if (count == 1) {
                String singleSiteName = AdmSiteQuery.findNameBySlcode(jdbi, slcode);
                if (singleSiteName != null) return singleSiteName;
            }
        }
        return defaultSiteName;
    }
}
