package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code GetOtherDetails}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtherDetailsQuery {
    @JsonProperty("_dc")
    private long cacheBuster;
    @JsonProperty("ownerSiteCode")
    private int ownerSiteCode;
    @JsonProperty("ownerOuCode")
    private int ownerOuCode;
    @JsonProperty("vendorCode")
    private int vendorCode;
    @JsonProperty("transactionDate")
    private String transactionDate;
    @JsonProperty("serviceType")
    private String serviceType;
}
