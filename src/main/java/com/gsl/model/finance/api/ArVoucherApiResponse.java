package com.gsl.model.finance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API response DTO returned after an AR Voucher creation call — the JSON reply shape, not a DB
 * row. {@code @JsonIgnoreProperties(ignoreUnknown = true)} lets the response tolerate extra
 * fields the API returns that this framework doesn't need, without breaking deserialization.
 *
 * @see ArVoucherApiRequest
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArVoucherApiResponse {

    /** The created voucher's document number (mirrors {@code finvchmain.scheme_docno}). */
    private String voucherNo;

    /** The voucher's document date, as returned by the API. */
    private String docDate;

    /** API-reported status of the create call/voucher. */
    private String status;
}
