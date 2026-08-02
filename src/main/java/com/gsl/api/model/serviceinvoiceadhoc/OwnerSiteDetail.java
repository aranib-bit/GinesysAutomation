package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of GET /ServiceInvoice/GetOwnerSiteOtherDetails. Trade group is NOT part of this
 * response (confirmed via HAR) — it comes from GetOtherDetails instead, once the vendor is known.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OwnerSiteDetail {
    @JsonProperty("code")
    private int ownerSiteCode;
    private String ownerSiteGstinNo;
    private String ownerSiteGstinStateCode;
    private String siteType;
}
