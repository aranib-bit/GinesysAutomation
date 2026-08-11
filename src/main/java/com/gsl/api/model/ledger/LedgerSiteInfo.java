package com.gsl.api.model.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code LedgerCreateRequest.ledgerSiteInfo} — shape confirmed via HAR
 * ({@code CreateExpenseSiteApplicablestage-ideal...har}): {@code {"siteCode":42}}. A non-empty
 * {@code ledgerSiteInfo} list is what makes a ledger "Site Applicable" server-side — there is no
 * separate boolean flag for it (see {@code LedgerCreateRequest}'s class Javadoc).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerSiteInfo {
    private long siteCode;
}
