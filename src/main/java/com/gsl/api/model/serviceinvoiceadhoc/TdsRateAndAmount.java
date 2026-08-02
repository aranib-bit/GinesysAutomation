package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of GetCalculatedTdsRateAndAmount's response (a bare array, empty when TDS isn't
 * applicable for the vendor — confirmed via HAR). Field names assumed to mirror Save's own
 * {@code tdsRate}/{@code tdsAmt}/{@code tdsGlCode}/{@code tdsSlCode} fields, which this endpoint
 * exists specifically to compute — not confirmed against a non-empty HAR sample since the
 * captured vendor had {@code tdsAppl="N"}; revisit if a tdsAppl="Y" vendor surfaces a mismatch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsRateAndAmount {
    private Integer tdsRate;
    private Double tdsAmt;
    private Integer tdsGlCode;
    private Integer tdsSlCode;
}
