package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested {@code refSite} object from GetOtherDetails — also embedded verbatim as both
 * {@code refSite} and (reused, see orchestration comment) {@code ownerSite} in Save's request.
 * Field names here are kept identical to the real JSON keys (including the underscored
 * {@code admou_Code}/{@code ship_CTName}), which are valid Java identifiers, to avoid an
 * annotation on every single field of a pass-through-only object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefSite {
    private String name;
    private String sitename;
    private String shrtname;
    private String siteType;
    private Integer admou_Code;
    private String ouname;
    private String slcode;
    private String ext;
    private Integer siteTaxCode;
    private String slname;
    private Integer ouTaxCode;
    private String isSecondary;
    private String isPos;
    private String operationStartDate;
    private String siteDocIn;
    private String address;
    private String city;
    private String reserveInventory;
    private String storeLocCode;
    private String gstinno;
    private String gstinStatus;
    private String taxPayerType;
    private String gstinVerifiedOn;
    private String stateCode;
    private String installationType;
    private String isBillingShippingSame;
    private String ship_CTName;
    private Integer admGstinCode;
    private String allowAuthorizeOfSelfCreatedTransaction;
    private int code;
}
