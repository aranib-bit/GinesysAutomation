package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.serviceinvoiceadhoc.TagSite;
import com.gsl.api.model.serviceinvoiceadhoc.SimpleServiceCharge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** One service line of {@link TdsCalculationRequest}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsCalcLine {
    @JsonProperty("setIndex")
    private int setIndex;
    @JsonProperty("index")
    private int index;
    @JsonProperty("rowIndex")
    private int rowIndex;
    @JsonProperty("code")
    private int code;
    @JsonProperty("status")
    private String status;
    @JsonProperty("srvAmt")
    private int srvAmt;
    @JsonProperty("quantity")
    private Integer quantity;
    @JsonProperty("glCode")
    private Integer glCode;
    @JsonProperty("gstItcAppl")
    private String gstItcAppl;
    @JsonProperty("hsnSacCode")
    private String hsnSacCode;
    @JsonProperty("rate")
    private Double rate;
    @JsonProperty("serviceCode")
    private int serviceCode;
    @JsonProperty("slCode")
    private Integer slCode;
    @JsonProperty("taxCode")
    private Integer taxCode;
    @JsonProperty("tdsSection")
    private String tdsSection;
    @JsonProperty("tagSites")
    private List<TagSite> tagSites;
    @JsonProperty("serviceName")
    private String serviceName;
    @JsonProperty("serviceCharges")
    private List<SimpleServiceCharge> serviceCharges;
}
