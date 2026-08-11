package com.gsl.api.model.ledger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item of {@code POST /Shared/GetSitesForFinanceLookup}'s {@code result.items} — shape
 * confirmed via HAR ({@code CreateExpenseSiteApplicablestage-ideal...har}). Only the fields
 * {@code LedgerApiClient} actually needs are mapped; the rest of the ~30-field real payload
 * (address/GSTIN/tax fields, irrelevant here) is ignored, same convention as
 * {@code CashBankLedgerLookupResult}/{@code SubLedgerLookupResult}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SiteLookupResult {
    private long code;
    private String name;
    private String shrtname;
    private String siteType;
    private String ext;
}
