package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of GET /ServiceInvoice/GetOtherDetails — the real source of {@code tradeGroupCode},
 * {@code term}, {@code refSite}, {@code tdsSource}, and vendor-specific TDS/GSTIN defaults once
 * a vendor is known. {@code summaryDetails} is intentionally omitted — UI-display only, not
 * consumed by anything downstream.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetOtherDetailsResult {
    private TradeGroup tradeGroup;
    private Term term;
    private RefSite refSite;
    private String cpGstinNo;
    private String ownerSiteGstinNo;
    private String ownerSiteGstinStateCode;
    private String cpGstinStateCode;
    private String tdsAppl;
    private TdsSource tdsSource;
    private int creditDays;
    private String dueDateBasis;
    private Integer admCurrencyCode;
    private double exchangeRate;
}
