package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET Finance/api/subledgers item shape — confirmed via HAR. Unlike Service Invoice Adhoc's
 * GetVendors (Supplier-only), this endpoint returns sub-ledgers of every classification
 * (classType 'C' Customer, 'S' Supplier, 'J' Jobber, 'O' Others, 'D' TDS Agency, etc.) — the
 * reason ArVoucherApiClient can backfill non-Supplier clsCodes that Service Invoice Adhoc cannot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubLedgerLookupResult {
    private long slCode;
    private String slName;
    private String slid;
    private long classCode;
    private String className;
    private String classType;
    private long glCode;
    private String glName;
    private String generalLedgerGroupName;
    private String generalLedgerAbbre;
}
