package com.gsl.model.finance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One sub-ledger line of an {@link ArVoucherApiRequest} — the JSON shape for a single
 * credit/adjustment entry, not a DB row (compare {@link com.gsl.model.finance.FinVchDet}, which
 * maps the resulting {@code finvchdet} line after the voucher is created).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArVoucherSubLedgerLine {

    /** Sub-ledger code this line credits/adjusts. */
    private String     subLedgerCode;

    /** GL code this line posts against. */
    private String     glCode;

    /** Credit amount for this sub-ledger line. */
    private BigDecimal creditAmount;

    /** Adjustment type for this line (e.g. settlement against a specific invoice/document). */
    private String     adjustmentType;
}
