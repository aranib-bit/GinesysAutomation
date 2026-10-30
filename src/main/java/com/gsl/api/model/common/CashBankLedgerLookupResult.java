package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET Finance/api/ledgers/cashbankledgers item shape — confirmed via HAR. Used by both
 * {@code ApVoucherApiClient} and {@code ArVoucherApiClient} to resolve the {@code cashBankGLCode}
 * a create request posts against.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashBankLedgerLookupResult {
    private long glCode;
    private String glName;
    private String abbreviation;
    /** Coded ledger type (e.g. Cash vs. Bank) with its display name below. */
    private String type;
    private String typeDisplay;
    private String sourceType;
    private String sourceTypeDisplay;
    /** Label shown for the cheque field when this ledger is used with a non-Cash payment mode. */
    private String chequeLabel;
}
