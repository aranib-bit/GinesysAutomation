package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST /ServiceInvoice/GetCalculatedTdsRateAndAmount} body. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsCalculationRequest {
    @JsonProperty("services")
    private List<TdsCalcLine> services;
    @JsonProperty("tdsSourceCode")
    private int tdsSourceCode;
    @JsonProperty("vendorCode")
    private int vendorCode;
    @JsonProperty("srvDate")
    private String srvDate;
    @JsonProperty("admOuCode")
    private int admOuCode;
    @JsonProperty("tdsAppAmt")
    private int tdsAppAmt;
    @JsonProperty("srvCode")
    private int srvCode;
}
