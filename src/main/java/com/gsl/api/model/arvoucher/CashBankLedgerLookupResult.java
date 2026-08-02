package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET Finance/api/ledgers/cashbankledgers item shape — confirmed via HAR. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashBankLedgerLookupResult {
    private long glCode;
    private String glName;
    private String abbreviation;
    private String type;
    private String typeDisplay;
    private String sourceType;
    private String sourceTypeDisplay;
    private String chequeLabel;
}
