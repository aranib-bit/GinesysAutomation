package com.gsl.api.model.serviceinvoiceadhoc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One Service Invoice Adhoc line — a service (not a stock item). Shape confirmed via HAR to be
 * shared identically between GetCalculatedTdsRateAndAmount's request {@code services[]} and the
 * final ServiceInvoice/Save request's own {@code services[]}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInvoiceAdhocLineItem {
    @Builder.Default
    private int setIndex = 0;
    @Builder.Default
    private int index = 0;
    @Builder.Default
    private int rowIndex = 0;
    @Builder.Default
    private int code = 0;
    @Builder.Default
    private String status = "N";
    private int srvAmt;
    private Integer quantity;
    private Integer glCode;
    private String gstItcAppl;
    private String hsnSacCode;
    private Integer rate;
    private int serviceCode;
    private Integer slCode;
    private Integer taxCode;
    @Builder.Default
    private String tdsSection = "";
    private List<TagSite> tagSites;
    private String serviceName;
    private List<SimpleServiceCharge> serviceCharges;
}
