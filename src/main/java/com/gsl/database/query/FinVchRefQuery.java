package com.gsl.database.query;

import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * Query helpers for {@code finvchmain.refno} (AR Voucher reference numbers).
 *
 * <p>Used by
 * {@link com.gsl.utils.dataprovider.fixture.finance.ArVoucherTemplateLoader}
 * to verify reference uniqueness when
 * {@link com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant#isAllowDuplicateReference()}
 * is {@code false}.
 */
public class FinVchRefQuery {

    private FinVchRefQuery() {}

    /**
     * Returns {@code true} if {@code reference} already exists in {@code finvchmain.refno}
     * for any of the given site codes.
     *
     * @param jdbi      active JDBI instance
     * @param reference the candidate reference string to check
     * @param siteCodes site codes scoped to the test user
     * @return {@code true} if the reference is already in use, {@code false} if it is free
     */
    public static boolean isReferenceUsed(Jdbi jdbi, String reference, List<Long> siteCodes) {
        String sql =
            "SELECT COUNT(*) FROM finvchmain " +
            "WHERE refno = :ref " +
            "  AND admsite_code_owner IN (<siteCodes>)";

        Long count = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("ref", reference)
                .bindList("siteCodes", siteCodes)
                .mapTo(Long.class)
                .one()
        );
        return count != null && count > 0;
    }
}
