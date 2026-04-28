package com.gsl.database.query;

import com.gsl.database.dialect.DbDialect;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.model.finance.PendingSlDocument;
import com.gsl.model.finance.SlNameWithId;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static query helpers for main.v_pending_adj.
 *
 * Uses {@code jdbi.withHandle()} + {@link DbDialect} instead of {@code @SqlQuery}
 * because the SQL structure varies by database dialect (castToDate, LIMIT vs FETCH FIRST).
 */
public class PendingAdjQuery {

    private PendingAdjQuery() {}

    /**
     * Query 1 — SL names with pending AR (for SubLedgerInformation list).
     * Returns up to {@code limit} distinct (slName, slId) pairs that have at least one
     * pending debit document with a non-future date, scoped to the given owner site codes.
     *
     * <p>Both columns are included so the caller can distinguish two sub-ledgers that
     * share the same display name — uniqueness is the (slname, slid) pair.
     *
     * <p>{@code ownerSiteCodes} mirrors the UI's {@code ReferenceSiteCode = ANY(:siteCodes)}
     * filter — only documents whose {@code ReferenceSiteCode} matches the AR Voucher's owner
     * site will appear in the Select Document popup, so we apply the same constraint here.
     *
     * <p>Extinct sub-ledgers ({@code finsl.ext = 'Y'}) are excluded via a JOIN on {@code finsl}
     * so the returned IDs always match what is visible in the UI.
     */
    public static List<SlNameWithId> findSlNamesWithPendingAR(
            Jdbi jdbi, List<Long> ouCodes, List<Long> ownerSiteCodes, int limit) {

        DbDialect d = DbDialectFactory.current();
        // SELECT DISTINCT pa.slname, pa.slid — ORDER BY pa.slname (which IS in the select list,
        // satisfying the PostgreSQL rule: "for SELECT DISTINCT, ORDER BY expressions
        // must appear in the select list").
        // JOIN finsl fs filters out extinct SLs (fs.ext = 'N' = active).
        String sql =
            "SELECT DISTINCT pa.slname, pa.slid " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "ORDER BY pa.slname " +
            d.limitBind("lim");

        return jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bind("lim", limit)
                .map((rs, ctx) -> new SlNameWithId(
                        rs.getString("slname"),
                        rs.getString("slid")))
                .list()
        );
    }

    /**
     * Query 2 — Open AR documents per (slName, slId) pair (for selectDocumentList).
     *
     * Fetches the actual pending invoice rows for the given SL name+ID pairs so the
     * TemplateLoader can pre-populate {@code selectDocumentList} with real document
     * numbers. The UI popup then uses these numbers to tick the correct rows.
     *
     * <p>Rows are filtered by slname IN (...) at the DB level, then post-filtered
     * in Java to match the exact (slname, slid) pair — eliminating false matches
     * when two sub-ledgers share the same display name but have different IDs.
     *
     * <p>The result map is keyed by the composite string {@code slName + "|" + slId}.
     * Use {@link #compositeKey(String, String)} to build lookup keys consistently.
     *
     * <p>{@code ownerSiteCodes} mirrors the UI's {@code ReferenceSiteCode = ANY(:siteCodes)}
     * filter so only documents visible in the Select Document popup are returned.
     *
     * @param docsPerSl maximum number of documents to keep per (slName, slId) pair
     * @return Map: {@code "slName|slId"} → list of open documents (at most docsPerSl)
     */
    public static Map<String, List<PendingSlDocument>> findDocumentsForSlNames(
            Jdbi jdbi, List<Long> ouCodes, List<Long> ownerSiteCodes, List<SlNameWithId> slNames, int docsPerSl) {

        // Extract just the string names for the SQL IN clause
        List<String> nameList = slNames.stream()
                .map(SlNameWithId::getSlName)
                .distinct()
                .collect(Collectors.toList());

        // Build a Set of composite keys for O(1) post-filter lookup
        Set<String> validKeys = slNames.stream()
                .map(s -> compositeKey(s.getSlName(), s.getSlId()))
                .collect(Collectors.toSet());

        DbDialect d = DbDialectFactory.current();
        // JOIN finsl fs keeps only non-extinct SLs (fs.ext = 'N'), consistent with findSlNamesWithPendingAR.
        String sql =
            "SELECT pa.slname, pa.slid, pa.document_no, " +
            d.castToDate("pa.document_date") + " AS document_date, " +
            "pa.entype, pa.refno, pa.ref_admsite_name, pa.pending " +
            "FROM v_pending_adj pa " +
            "JOIN finsl fs ON fs.slname = pa.slname AND fs.slid = pa.slid AND fs.ext = 'N' " +
            "WHERE pa.admou_code IN (<ouCodes>) " +
            "  AND pa.ReferenceSiteCode IN (<ownerSiteCodes>) " +
            "  AND pa.slname IN (<nameList>) " +
            "  AND pa.debit_credit = 'D' " +
            "  AND pa.pending > 0 " +
            "  AND " + d.castToDate("pa.document_date") + " <= " + d.currentDate() + " " +
            "ORDER BY pa.slname, pa.document_date";

        List<PendingSlDocument> rows = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bindList("ouCodes", ouCodes)
                .bindList("ownerSiteCodes", ownerSiteCodes)
                .bindList("nameList", nameList)
                .map((rs, ctx) -> {
                    PendingSlDocument doc = new PendingSlDocument();
                    doc.setSlName(rs.getString("slname"));
                    doc.setSlId(rs.getString("slid"));
                    doc.setDocumentNo(rs.getString("document_no"));
                    java.sql.Date sqlDate = rs.getDate("document_date");
                    doc.setDocumentDate(sqlDate != null ? sqlDate.toLocalDate() : null);
                    doc.setEntype(rs.getString("entype"));
                    doc.setRefNo(rs.getString("refno"));
                    doc.setReferenceSite(rs.getString("ref_admsite_name"));
                    doc.setPending(rs.getBigDecimal("pending"));
                    return doc;
                })
                .list()
        );

        // Post-filter: keep only rows whose (slName, slId) pair is in our input list
        // then group by composite key, capping at docsPerSl per group
        return rows.stream()
                .filter(doc -> validKeys.contains(compositeKey(doc.getSlName(), doc.getSlId())))
                .collect(
                    Collectors.groupingBy(
                        doc -> compositeKey(doc.getSlName(), doc.getSlId()),
                        Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> list.stream().limit(docsPerSl).collect(Collectors.toList())
                        )
                    )
                );
    }

    /**
     * Builds the composite map key used by {@link #findDocumentsForSlNames}.
     * Call this from any code that needs to look up documents in the returned map.
     */
    public static String compositeKey(String slName, String slId) {
        return slName + "|" + slId;
    }
}
