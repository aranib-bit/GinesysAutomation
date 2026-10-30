package com.gsl.api.webapi.model.request.serviceinvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST /ServiceInvoice/ValidateReferenceNo} body; {@code invoiceCode} is 0 for a new invoice. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ValidateReferenceNoRequest {
    @JsonProperty("ownerSiteCode")
    private int ownerSiteCode;
    @JsonProperty("vendorCode")
    private int vendorCode;
    @JsonProperty("invoiceDate")
    private String invoiceDate;
    @JsonProperty("referenceNo")
    private String referenceNo;
    @JsonProperty("invoiceCode")
    private int invoiceCode;
}
