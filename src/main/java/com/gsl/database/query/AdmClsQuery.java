package com.gsl.database.query;

import com.gsl.database.dialect.DbDialect;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.model.finance.AdmCls;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.SlNameWithId;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * Static query helpers for the {@code admcls} classification table and
 * its relationship to {@code finsl}.
 *
 * <p><b>Schema facts (confirmed):</b>
 * <ul>
 *   <li>Join: {@code finsl.clscode = admcls.clscode}</li>
 *   <li>{@code admcls.cmpcode = 1} → system-defined classification</li>
 *   <li>{@code admcls.cmpcode = 0} → user-defined classification</li>
 * </ul>
 *
 * <p><b>Discovery queries</b> ({@link #findAllFromAdmCls} and {@link #findAllFromFinSlWithClsCode})
 * are used to identify the actual {@code clscode} values in a given environment so that
 * {@link com.gsl.utils.dataprovider.fixture.finance.SelectDocumentCombinations} can be
 * populated with real values.
 */
public class AdmClsQuery {

    private AdmClsQuery() {}

    // ─── Query 1: SELECT clscode, cmpcode, * FROM admcls ORDER BY clscode ────

    /**
     * Returns all rows from {@code admcls} ordered by {@code clscode}.
     *
     * <p>Run this once per environment to discover the available classification codes
     * and populate the {@code slClsCode} values in
     * {@link com.gsl.utils.dataprovider.fixture.finance.SelectDocumentCombinations}.
     *
     * <p>Equivalent SQL: {@code SELECT clscode, cmpcode, clsname FROM admcls ORDER BY clscode}
     *
     * @param jdbi active JDBI instance
     * @return all admcls rows ordered by clscode
     */
    public static List<AdmCls> findAllFromAdmCls(Jdbi jdbi) {
        String sql =
            "SELECT clscode, cmpcode, clsname " +
            "FROM admcls " +
            "ORDER BY clscode";

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .map((rs, ctx) -> {
                    AdmCls ac = new AdmCls();
                    ac.setClscode(rs.getLong("clscode"));
                    ac.setCmpcode(rs.getInt("cmpcode"));
                    ac.setClsname(rs.getString("clsname"));
                    return ac;
                })
                .list()
        );
    }

    // ─── Query: single admcls row by clscode ─────────────────────────────────

    /**
     * Fetches one {@link AdmCls} record by its primary key.
     *
     * <p>Used at runtime by {@code ArVoucherTemplateLoader.buildDynamicTestCaseName}
     * to resolve the human-readable {@code clsname} and the System/User label from
     * {@code cmpcode} for a given {@code slClsCode} variant.
     *
     * @param jdbi    active JDBI instance
     * @param clscode the admcls primary key to look up
     * @return the matching AdmCls, or empty if not found
     */
    public static java.util.Optional<AdmCls> findByClsCode(Jdbi jdbi, long clscode) {
        String sql =
            "SELECT clscode, cmpcode, clsname, clstype, ext " +
            "FROM admcls " +
            "WHERE clscode = :clscode";

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("clscode", clscode)
                .map((rs, ctx) -> {
                    AdmCls ac = new AdmCls();
                    ac.setClscode(rs.getLong("clscode"));
                    ac.setCmpcode(rs.getInt("cmpcode"));
                    ac.setClsname(rs.getString("clsname"));
                    ac.setClstype(rs.getString("clstype"));
                    ac.setExt(rs.getString("ext"));
                    return ac;
                })
                .findFirst()
        );
    }

    // ─── Query 2: SELECT clscode, cmpcode, * FROM finsl ──────────────────────

    /**
     * Returns all rows from {@code finsl} that carry a {@code clscode}, ordered by slcode.
     *
     * <p>Cross-reference with {@link #findAllFromAdmCls} to see which classification each
     * sub-ledger belongs to.
     *
     * <p>Equivalent SQL:
     * {@code SELECT slcode, slid, slname, glcode, clscode, cmpcode FROM finsl ORDER BY slcode}
     *
     * @param jdbi active JDBI instance
     * @return all finsl rows with clscode/cmpcode populated
     */
    public static List<FinSl> findAllFromFinSlWithClsCode(Jdbi jdbi) {
        String sql =
            "SELECT slcode, slid, slname, glcode, clscode, cmpcode " +
            "FROM finsl " +
            "ORDER BY slcode";

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .map((rs, ctx) -> {
                    FinSl fs = new FinSl();
                    fs.setSlcode(rs.getLong("slcode"));
                    fs.setSlid(rs.getString("slid"));
                    fs.setSlname(rs.getString("slname"));
                    fs.setGlcode(rs.getLong("glcode"));
                    fs.setClscode(rs.getLong("clscode"));
                    fs.setCmpcode(rs.getInt("cmpcode"));
                    return fs;
                })
                .list()
        );
    }

    // ─── Fixture-factory query: SL names filtered by admcls.clscode ──────────

    /**
     * Returns distinct {@code (slName, slId)} pairs with pending AR documents,
     * filtered to sub-ledgers whose classification matches {@code clsCode},
     * scoped to the given owner site via {@code ReferenceSiteCode}.
     *
     * <p>Join: {@code finsl.clscode = admcls.clscode}
     *
     * <p>Used by
     * {@link com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource#loadFreshForScenario}
     * when a {@link com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant}
     * has a non-null {@code slClsCode}.
     *
     * <p>{@code ownerSiteCodes} mirrors the UI's {@code ReferenceSiteCode = ANY(:siteCodes)}
     * filter so only SLs with documents visible in the Select Document popup are returned.
     *
     * @param jdbi           active JDBI instance
     * @param ouCodes        OU codes scoped to the test user
     * @param ownerSiteCodes owner site codes (AR Voucher's selected owner site)
     * @param clsCode        the {@code admcls.clscode} value to filter on (e.g. 1, 2, 3 …)
     * @param limit          maximum number of (slName, slId) pairs to return
     * @return matching SL name+id pairs ordered by slname
     */
    public static List<SlNameWithId> findSlNamesWithPendingARByClsCode(
            Jdbi jdbi, List<Long> ouCodes, List<Long> ownerSiteCodes, long clsCode, int limit) {

        DbDialect d = DbDialectFactory.current();

        String sql =
            "SELECT DISTINCT pa.slname, pa.slid " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "  AND fs.clscode = :clsCode " +
            "  AND fs.ext = 'N' " +
            "ORDER BY pa.slname " +
            d.limitBind("lim");

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bind("clsCode", clsCode)
                .bind("lim", limit)
                .map((rs, ctx) -> new SlNameWithId(
                        rs.getString("slname"),
                        rs.getString("slid")))
                .list()
        );
    }

    // ─── Runtime helper: resolve clsname for a single SL ────────────────────

    /**
     * Returns the {@code admcls.clsname} (e.g. "Customer", "Supplier") for a given
     * sub-ledger ID by joining {@code finsl} with {@code admcls}.
     *
     * <p>Used by {@code ArVoucherTemplateLoader} to populate
     * {@link com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation#slType}
     * at data-build time so the classification is visible in Extent Report and console logs.
     *
     * @param jdbi active JDBI instance
     * @param slId the {@code finsl.slid} value (e.g. "217")
     * @return the class name, or {@code null} if not found
     */
    public static String findClsnameBySlId(Jdbi jdbi, String slId) {
        if (slId == null || slId.isBlank()) return null;
        String sql =
            "SELECT ac.clsname " +
            "FROM finsl fs " +
            "JOIN admcls ac ON ac.clscode = fs.clscode " +
            "WHERE fs.slid = :slId " +
            "LIMIT 1";
        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("slId", slId)
                .mapTo(String.class)
                .findFirst()
                .orElse(null)
        );
    }

    // ─── Discovery helper: distinct clscodes present in pending AR ───────────

    /**
     * Returns all distinct {@code admcls} classification codes that have at least one
     * sub-ledger with pending AR documents for the given OU codes.
     *
     * <p>Use this to log which SL types are available in the test environment when
     * debugging missing test data for a specific {@code slClsCode} variant.
     *
     * @param jdbi    active JDBI instance
     * @param ouCodes OU codes scoped to the test user
     * @return distinct AdmCls entries (clscode + cmpcode + name) found in pending AR
     */
    public static List<AdmCls> findDistinctClsCodesInPendingAR(
            Jdbi jdbi, List<Long> ouCodes) {

        String sql =
            "SELECT DISTINCT ac.clscode, ac.cmpcode, ac.clsname, ac.clstype, ac.ext " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "JOIN admcls ac ON ac.clscode = fs.clscode " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND ac.ext = 'N' " +
            "ORDER BY ac.clscode";

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .map((rs, ctx) -> {
                    AdmCls ac = new AdmCls();
                    ac.setClscode(rs.getLong("clscode"));
                    ac.setCmpcode(rs.getInt("cmpcode"));
                    ac.setClsname(rs.getString("clsname"));
                    ac.setClstype(rs.getString("clstype"));
                    ac.setExt(rs.getString("ext"));
                    return ac;
                })
                .list()
        );
    }

    // ─── Distinct slcode count helpers (used by DocumentSelector for dynamic retry bound) ──

    /**
     * Returns the count of distinct {@code slcode} values in {@code v_pending_adj}
     * filtered to sub-ledgers whose classification matches {@code clsCode}.
     *
     * <p>Applies the same filters as {@link #findSlNamesWithPendingARByClsCode} so the
     * count is consistent with the SL discovery query used by that routing branch.
     */
    public static long countDistinctSlCodesByClsCode(
            Jdbi jdbi, List<Long> ouCodes, List<Long> ownerSiteCodes, long clsCode) {

        DbDialect d = DbDialectFactory.current();
        String sql =
            "SELECT COUNT(DISTINCT pa.slcode) " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "  AND fs.clscode = :clsCode " +
            "  AND fs.ext = 'N'";

        return jdbi.withHandle(h ->
            h.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bind("clsCode", clsCode)
                .mapTo(Long.class)
                .one()
        );
    }

    /**
     * Returns the count of distinct {@code slcode} values in {@code v_pending_adj}
     * filtered to sub-ledgers whose classification matches {@code cmpCode}.
     *
     * <p>Applies the same filters as {@link #findSlNamesWithPendingARByCmpCode} so the
     * count is consistent with the SL discovery query used by that routing branch.
     */
    public static long countDistinctSlCodesByCmpCode(
            Jdbi jdbi, List<Long> ouCodes, int cmpCode) {

        DbDialect d = DbDialectFactory.current();
        String sql =
            "SELECT COUNT(DISTINCT pa.slcode) " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "JOIN admcls ac ON ac.clscode = fs.clscode " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "  AND ac.cmpcode = :cmpCode " +
            "  AND ac.ext = 'N'";

        return jdbi.withHandle(h ->
            h.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bind("cmpCode", cmpCode)
                .mapTo(Long.class)
                .one()
        );
    }

    // ─── Fixture-factory query: SL names filtered by admcls.cmpcode ──────────

    /**
     * Returns distinct {@code (slName, slId)} pairs with pending AR documents,
     * filtered to sub-ledgers whose classification matches {@code cmpCode}.
     *
     * <p>{@code cmpCode = 1} → system-defined SLs (Customer, Supplier, etc.)
     * <p>{@code cmpCode = 0} → user-defined SLs (TDS, Staff, etc.)
     *
     * <p>Used by FIFO, REF_SITEWISE, and NO_ADJUSTMENT mandatory baseline placeholders
     * that need to scope to a specific SL category without pinning a concrete {@code clscode}.
     *
     * @param jdbi    active JDBI instance
     * @param ouCodes OU codes scoped to the test user
     * @param cmpCode 1 = system-defined, 0 = user-defined
     * @param limit   maximum number of (slName, slId) pairs to return
     * @return matching SL name+id pairs ordered by slname
     */
    public static List<SlNameWithId> findSlNamesWithPendingARByCmpCode(
            Jdbi jdbi, List<Long> ouCodes, int cmpCode, int limit) {

        DbDialect d = DbDialectFactory.current();

        String sql =
            "SELECT DISTINCT pa.slname, pa.slid " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid " +
            "JOIN admcls ac ON ac.clscode = fs.clscode " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "  AND ac.cmpcode = :cmpCode " +
            "  AND ac.ext = 'N' " +
            "ORDER BY pa.slname " +
            d.limitBind("lim");

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bind("cmpCode", cmpCode)
                .bind("lim", limit)
                .map((rs, ctx) -> new SlNameWithId(
                        rs.getString("slname"),
                        rs.getString("slid")))
                .list()
        );
    }
}
