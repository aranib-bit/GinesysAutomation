package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

/**
 * Static query helper for validating UDF value uniqueness against {@code main.finvchmain} —
 * backs {@code ArVoucherApiClient}'s handling of {@code isUnique} UDF fields (AP Voucher/AR
 * Voucher share the {@code FINVCHMAIN} UDF table). Mirrors the static-query-class convention
 * already used throughout this package (AdmClsQuery, PendingAdjQuery, ReferenceSiteQuery, ...).
 */
public final class UdfValueQuery {

    private UdfValueQuery() {}

    /**
     * Returns {@code true} if {@code value} already exists in {@code finvchmain}'s given UDF
     * column. {@code columnName} always originates from {@code GetUDFMetaData} (server-controlled,
     * e.g. "UDFSTRING01"), but is validated against a fixed allow-list pattern before being
     * interpolated into SQL anyway — same defense-in-depth precedent already documented on
     * {@code DbApVoucherDataSource.findApVoucherDocSchemeName}'s own dynamic IN-clause.
     *
     * @throws IllegalArgumentException if columnName doesn't match a recognized UDF column shape
     */
    public static boolean existsInFinVchMain(Jdbi jdbi, String columnName, Object value) {
        if (columnName == null || !columnName.matches("(?i)^udf(string|num|date)\\d{2}$")) {
            throw new IllegalArgumentException("Refusing to query unrecognized UDF column: " + columnName);
        }
        String sql = "SELECT 1 FROM finvchmain WHERE " + columnName.toLowerCase() + " = :value LIMIT 1";
        return jdbi.withHandle(h ->
                h.createQuery(sql)
                        .bind("value", value)
                        .mapTo(Integer.class)
                        .findFirst()
                        .isPresent()
        );
    }
}
