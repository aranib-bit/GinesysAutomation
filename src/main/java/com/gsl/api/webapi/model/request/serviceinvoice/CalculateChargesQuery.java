package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code CalculateServiceCharges}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalculateChargesQuery {
    @JsonProperty("docSchemeCode")
    private int docSchemeCode;
    @JsonProperty("vendorCode")
    private int vendorCode;
    @JsonProperty("siteCode")
    private int siteCode;
    @JsonProperty("tranDate")
    private String tranDate;
    @JsonProperty("tradeGroupCode")
    private int tradeGroupCode;
    @JsonProperty("termCode")
    private int termCode;
    @JsonProperty("formCode")
    private int formCode;
    @JsonProperty("isExciseApplicable")
    private boolean isExciseApplicable;
    @JsonProperty("exchangeRate")
    private double exchangeRate;
}
