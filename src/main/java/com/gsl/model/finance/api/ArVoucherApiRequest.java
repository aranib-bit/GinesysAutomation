package com.gsl.model.finance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API request DTO for creating an AR Voucher — the JSON body shape, not a DB row (compare
 * {@link com.gsl.model.finance.FinVchMain}, which maps the resulting {@code finvchmain} header).
 *
 * <p>{@code @JsonInclude(NON_NULL)} drops unset fields from the outgoing JSON rather than
 * serializing them as {@code null}, so builders can omit optional fields freely.
 *
 * <ul>
 *   <li>{@link ArVoucherSubLedgerLine}</li>
 *   <li>{@link ArVoucherApiResponse}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArVoucherApiRequest {

    /** Voucher date, as formatted for the API's expected date string. */
    private String voucherDate;

    /** Reference number for the voucher header. */
    private String reference;

    /** Header narration/remarks. */
    private String remarks;

    /** Cash/Bank GL code the voucher is posted against. */
    private String cashBankCode;

    /** One entry per sub-ledger line to credit/adjust on this voucher. */
    private List<ArVoucherSubLedgerLine> subLedgerLines;
}
