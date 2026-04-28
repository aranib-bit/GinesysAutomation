package com.gsl.utils.cheque;

import java.util.List;

/**
 * Catalog of negative / edge-case cheque numbers for validation testing.
 *
 * Separation guarantee: this class is never referenced by ChequeStrategyRotator,
 * UniqueChequeGenerator, or the AR Voucher fixture builder. It is consumed only
 * when a dedicated negative test explicitly calls getAll().
 */
public final class ChequeEdgeCases {

    private ChequeEdgeCases() {}

    public static List<ChequeEdgeCase> getAll() {
        return List.of(
            new ChequeEdgeCase("EMPTY",            ""),
            new ChequeEdgeCase("NULL",             null),
            new ChequeEdgeCase("SHORT_LENGTH",     "12"),
            new ChequeEdgeCase("LONG_LENGTH",      "123456789012"),
            new ChequeEdgeCase("ALPHANUMERIC",     "AB1234"),
            new ChequeEdgeCase("SPECIAL_CHAR",     "12@#45"),
            new ChequeEdgeCase("ALL_ZERO",         "000000"),
            new ChequeEdgeCase("LEADING_ZERO_ONLY","000123"),
            new ChequeEdgeCase("DUPLICATE",        "123456")
        );
    }
}
