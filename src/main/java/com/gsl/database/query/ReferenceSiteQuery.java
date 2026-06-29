package com.gsl.database.query;

import com.gsl.database.ReferenceSiteAmountRegistry;
import com.gsl.database.dialect.DbDialect;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestration layer for resolving the Reference Site name per sub-ledger,
 * shared by both AP Voucher and AR Voucher data sources.
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

    /**
     * Queries {@code v_pending_adj} for the given SL, grouped by {@code ref_admsite_name},
     * and returns the name of the first reference site for which
     * {@link ReferenceSiteAmountRegistry#tryReserve} succeeds.
     *
     * <p>Sites are ordered by descending pending amount so the largest pool is tried first.
     * Returns {@code null} if all candidates are already fully reserved by parallel threads
     * or if the SL has no eligible documents in {@code v_pending_adj}.
     *
     * <p>Shared by AP and AR Voucher data sources for the Reference Sitewise FIFO scenario;
     * the {@code debit_credit} filter is parameterised because AP credits ({@code 'C'}) and
     * AR debits ({@code 'D'}) drive opposite pending balances.
     *
     * @param jdbi        active JDBI instance
     * @param d           dialect (controls {@code castToDate}, {@code currentDate}, {@code limitBind})
     * @param slCode      numeric {@code finsl.slcode} for the sub-ledger
     * @param ouCodes     user's OU codes — scopes the query to the user's organisation
     * @param debitCredit {@code "C"} for AP (credit-balance pending), {@code "D"} for AR
     * @param slPending   total pending amount for this SL (sum of {@code v_pending_adj.pending})
     * @param slName      sub-ledger name (used as registry key component)
     * @param slId        sub-ledger ID (used as registry key component)
     * @return the resolved reference site name, or {@code null} if none reservable
     */
    public static String findBestRefSiteForSl(Jdbi jdbi, DbDialect d,
                                              long slCode, List<Long> ouCodes,
                                              String debitCredit,
                                              BigDecimal slPending,
                                              String slName, String slId) {
        if (ouCodes == null || ouCodes.isEmpty()) return null;

        String inList = ouCodes.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String sql =
            "SELECT ref_admsite_name, SUM(pending) AS site_pending " +
            "FROM v_pending_adj " +
            "WHERE slcode = :slCode " +
            "  AND admou_code IN (" + inList + ") " +
            "  AND debit_credit = :debitCredit " +
            "  AND pending > 0 " +
            "  AND " + d.castToDate("document_date") + " <= " + d.currentDate() + " " +
            "  AND ref_admsite_name IS NOT NULL " +
            "GROUP BY ref_admsite_name " +
            "ORDER BY site_pending DESC " +
            d.limitBind("lim");

        List<String[]> candidates = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("slCode", slCode)
                .bind("debitCredit", debitCredit)
                .bind("lim", 10)
                .map((rs, ctx) -> new String[]{
                        rs.getString("ref_admsite_name"),
                        rs.getBigDecimal("site_pending").toPlainString()})
                .list()
        );

        for (String[] row : candidates) {
            String refSiteName = row[0];
            BigDecimal sitePending = new BigDecimal(row[1]);
            // Use slPending as the reservation amount — voucher amount equals total SL pending.
            // Registry ceiling = sitePending (can't adjust more than ref site has pending).
            BigDecimal ceiling = sitePending.min(slPending.compareTo(BigDecimal.ZERO) > 0
                    ? slPending : sitePending);
            if (ReferenceSiteAmountRegistry.tryReserve(slName, slId, refSiteName, ceiling, sitePending)) {
                return refSiteName;
            }
        }
        return null;
    }
}
