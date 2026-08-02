package com.gsl.api.model.serviceinvoiceadhoc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/**
 * POST ServiceInvoice/Save request body — full shape confirmed via HAR (mirrors
 * flows/serviceInvoiceAdhoc.js). Site/vendor/trade-group/term/TDS-source details are nested
 * objects sourced live from earlier steps in the flow (GetOwnerSiteOtherDetails, GetVendors,
 * GetOtherDetails), never hardcoded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInvoiceAdhocCreateRequest {
    @Builder.Default
    private int code = 0;
    private int admOuCode;
    @Builder.Default
    private String certificateNo = "";
    private String cpGstinNo;
    private String cpGstinStateCode;
    @Builder.Default
    private int dataVersion = 0;
    private String documentDate;
    private String documentNo;
    private String dueDate;
    @Builder.Default
    private String entrySource = "";
    private String gstAppl;
    private String modifiedOn;
    private String ownerSiteGstinNo;
    private String ownerSiteGstinStateCode;
    @Builder.Default
    private String refPeriod = "";
    @Builder.Default
    private String releaseStatus = "";
    private String releaseOn;
    @Builder.Default
    private String remarks = "";
    @Builder.Default
    private String schemeDocNo = "";
    private String srvDate;
    private String tdsAppl;
    @Builder.Default
    private String tdsSection = "";
    private String createdOn;
    /** PURSRVMAIN.type CHECK constraint restricts this to J/L/O/C/N — see ServiceInvoiceAdhocApiClient. */
    private String type;
    @Builder.Default
    private int ycode = 0;
    @Builder.Default
    private boolean calculateChargeOnTermForOrder = false;
    private String id;

    /** ServiceInvoice/Save's ownerSite is a distinct, reduced shape from refSite — see OwnerSiteForSave. */
    private OwnerSiteForSave ownerSite;
    private RefSite refSite;
    private TradeGroup tradeGroup;
    private Term term;
    private VendorLookupResult pcode;
    private TdsSource tdsSource;
    private String tdsType;

    private String dueDateBasis;
    private int creditDays;
    @Builder.Default
    private boolean canModify = true;
    @Builder.Default
    private String allowAuthorize = "Y";

    private Integer tdsRate;
    private Double tdsAmt;
    private Integer tdsGlCode;
    private Integer tdsSlCode;
    private String errorMsg;

    @Builder.Default
    private String entryMode = "Add";
    private Integer docSchemeCode;

    @Builder.Default
    private AttachedFiles attachedFiles = AttachedFiles.builder().build();
    @Builder.Default
    private boolean saveAndRelease = false;
    @Builder.Default
    private List<Object> udfDetails = List.of();

    @Singular
    private List<ServiceInvoiceAdhocLineItem> services;
    @Singular("headerCharge")
    private List<SaveHeaderCharge> headerCharges;
}
