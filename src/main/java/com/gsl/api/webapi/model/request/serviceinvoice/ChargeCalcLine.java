package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** One service line of {@link CalculateChargesRequest}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChargeCalcLine {
    @JsonProperty("code")
    private int code;
    @JsonProperty("serviceCode")
    private int serviceCode;
    @JsonProperty("serviceAmount")
    private int serviceAmount;
    @JsonProperty("recordIndex")
    private int recordIndex;
    @JsonProperty("serviceCharges")
    private List<Object> serviceCharges;
    @JsonProperty("glCode")
    private Integer glCode;
    @JsonProperty("slCode")
    private Integer slCode;
    @JsonProperty("quantity")
    private int quantity;
    @JsonProperty("gstItcAppl")
    private String gstItcAppl;
}
