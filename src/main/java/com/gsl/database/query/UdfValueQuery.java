package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

import java.util.Set;

/**
 * Static query helper for validating UDF value uniqueness against a UDF-bearing table — backs
 * {@code UdfValueBuilder}'s handling of {@code isUnique} UDF fields, shared by AR/AP Voucher
 * ({@code FINVCHMAIN}) and Sub Ledger Others ({@code FINSL}). Mirrors the static-query-class
 * convention already used throughout this package (AdmClsQuery, PendingAdjQuery,
 * ReferenceSiteQuery, ...).
 */
public final class UdfValueQuery {

    private UdfValueQuery() {}

    /** Tables confirmed (via {@code GetUDFMetaData}'s own {@code tableName} param) to carry UDF columns. */
    private static final Set<String> KNOWN_UDF_TABLES = Set.of("FINVCHMAIN", "FINSL");

    /**
     * Returns {@code true} if {@code value} already exists in {@code tableName}'s given UDF
     * column. Both {@code tableName} and {@code columnName} always originate from
     * {@code GetUDFMetaData} (server-controlled, e.g. table "FINSL", column "UDFSTRING01"), but
     * are validated against fixed allow-lists before being interpolated into SQL anyway — same
     * defense-in-depth precedent already documented on
     * {@code DbApVoucherDataSource.findApVoucherDocSchemeName}'s own dynamic IN-clause.
     *
     * @throws IllegalArgumentException if tableName isn't a known UDF-bearing table, or
     *                                   columnName doesn't match a recognized UDF column shape
     */
    public static boolean existsInTable(Jdbi jdbi, String tableName, String columnName, Object value) {
        if (tableName == null || !KNOWN_UDF_TABLES.contains(tableName.toUpperCase())) {
            throw new IllegalArgumentException("Refusing to query unrecognized UDF table: " + tableName);
        }
        if (columnName == null || !columnName.matches("(?i)^udf(string|num|date)\\d{2}$")) {
            throw new IllegalArgumentException("Refusing to query unrecognized UDF column: " + columnName);
        }
        String sql = "SELECT 1 FROM " + tableName.toLowerCase() + " WHERE " + columnName.toLowerCase() + " = :value LIMIT 1";
        return jdbi.withHandle(h ->
                h.createQuery(sql)
                        .bind("value", value)
                        .mapTo(Integer.class)
                        .findFirst()
                        .isPresent()
        );
    }
}
