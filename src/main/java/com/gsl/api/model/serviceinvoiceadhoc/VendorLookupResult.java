package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row from POST /ServiceInvoice/GetVendors — resolved by matching {@code slid} against the
 * vendor SL code the backfill was given. This same object (with {@code id} set) is embedded
 * verbatim as the {@code pcode} field in the eventual ServiceInvoice/Save request, matching the
 * HAR exactly, so every field here is kept even though only a few are read directly in Java.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorLookupResult {
    private String name;
    private String slid;
    private String namewithSLID;
    private String abbreviation;
    private Integer clsCode;
    private String agCode;
    @JsonProperty("class")
    private String vendorClass;
    private String classType;
    private Integer glCode;
    private String city;
    private String state;
    private String zone;
    private String contactPerson;
    private Integer dueDays;
    private String ext;
    private String saleType;
    private String generalLedger;
    private String finSLGLExt;
    private String type;
    private String typeDisplay;
    private String classTypeDisplay;
    private String gateInAppl;
    private String lgtAppl;
    private String outLgtAppl;
    private Integer purchaseGlCode;
    private Integer purchaseSlCode;
    private String whetherConsignment;
    private Integer siteCode;
    private String ecomChannnelCode;
    private Integer deliveryBufferDays;
    private String shipmentAppl;
    private Double agRate;
    private Integer purchaseReturnGlCode;
    private Integer purchaseReturnSlCode;
    private Integer salesReturnGlCode;
    private Integer salesReturnSlCode;
    private String baddr;
    private Integer taxRegionCode;
    private Integer admCurrencyCode;
    private Double crAmount;
    private Double purchaseOrderLimit;
    private String ouLgtAppl;
    private Integer trpCode;
    private String transporter;
    @JsonProperty("cp_Gstin_State_Code")
    private String cpGstinStateCode;
    private String mobileNo;
    private Boolean isMarketPlace;
    private Integer slCode;
    private String taxPayerType;
    private String gstinStatus;
    private String gstinVerifiedOn;
    private String pan;
    @JsonProperty("cpgstinno")
    private String cpGstinNo;
    private String tdsAppl;
    /** Vendor code — distinct from {@code slid}, this is the numeric code used elsewhere (e.g. GetOtherDetails' vendorCode). */
    private int code;
    /** Not part of the GetVendors response — an ExtJS model id, set before embedding as Save's pcode. */
    private String id;
}
