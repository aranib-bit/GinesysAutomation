package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** {@code POST /ServiceInvoice/CalculateServiceCharges} body. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CalculateChargesRequest {
    @JsonProperty("services")
    private List<ChargeCalcLine> services;
    @JsonProperty("applicableItems")
    private List<Object> applicableItems;
    @JsonProperty("chargeData")
    private List<Object> chargeData;
    @JsonProperty("skipChargeCode")
    private List<Object> skipChargeCode;
}
