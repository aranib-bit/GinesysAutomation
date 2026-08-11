package com.gsl.api.model.subledgerothers;

import com.gsl.api.model.common.SimpleResultResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pairs the actual {@link SubLedgerOthersCreateRequest} sent with the {@link SimpleResultResponse}
 * it got back — same rationale as {@code LedgerCreationOutcome}: the Create response carries no
 * generated identifier, so the caller ({@code SubLedgerOthersBackfillStrategy}) can only find the
 * row it just created by re-using the unique {@code slname}/{@code slid} it generated up front.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubLedgerOthersCreationOutcome {
    private SubLedgerOthersCreateRequest request;
    private SimpleResultResponse response;
}
