package com.gsl.api.model.ledger;

import com.gsl.api.model.common.SimpleResultResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pairs the actual {@link LedgerCreateRequest} sent with the {@link SimpleResultResponse} it got
 * back. Needed because — unlike AR Voucher's Create response, which echoes a generated
 * {@code voucherCode} — {@code POST /Ledger/Create}'s response carries no identifier at all
 * ({@code {"result":true,"success":true,"error":null}}), so the caller (
 * {@code LedgerMasterBackfillStrategy}) can only find the row it just created by re-using the
 * unique {@code glname} it generated up front — which lives on the request, not the response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerCreationOutcome {
    private LedgerCreateRequest request;
    private SimpleResultResponse response;
}
