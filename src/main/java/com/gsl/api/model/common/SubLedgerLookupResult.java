package com.gsl.api.model.common;

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
    /** External sub-ledger id, distinct from {@code slCode} — carried alongside the name so a
     *  same-named sub-ledger can be disambiguated downstream, e.g. via {@code FinSlDao.findBySlnameAndSlid}. */
    private String slid;
    private long classCode;
    private String className;
    private String classType;
    private long glCode;
    private String glName;
    private String generalLedgerGroupName;
    private String generalLedgerAbbre;
    /** {@code finsl.tds_app} as the lookup reports it ("YES"/"NO"); replaces the DB-only TDS-applicability read. */
    private String tdsApp;
    /** The TDS source of a TDS-applicable party ({@code tdssrccode}); 0 when none. */
    private long sourceCode;

    /** True when the sub-ledger is TDS-applicable (the lookup's {@code tdsApp} starts with Y). */
    public boolean isTdsApplicable() {
        return tdsApp != null && tdsApp.trim().toUpperCase().startsWith("Y");
    }
}
