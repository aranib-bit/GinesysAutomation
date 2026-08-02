package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reduced per-charge shape used in two places (confirmed identical via HAR): the {@code services[]}
 * line inside GetCalculatedTdsRateAndAmount's request, and the {@code services[].serviceCharges}
 * inside the final ServiceInvoice/Save request. Uses {@code includeInTds} (lowercase "ds") —
 * distinct from CalculateServiceCharges' own response shape, which uses {@code includeInTDS}
 * (see {@link ServiceChargeDetail}) — do not conflate the two.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimpleServiceCharge {
    private double appAmount;
    private String basis;
    private int chargeCode;
    private double chargeAmount;
    private String formulae;
    private int glCode;
    private String gstComponent;
    private String includeInTds;
    private String isReverse;
    private String isTax;
    private String operationLevel;
    private double rate;
    private Double roundoffAdjAmount;
    private int seq;
    private String sign;
    private Integer slCode;
    private String source;
    private String isRoundOff;
}
