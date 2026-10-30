package com.gsl.api.model.documentadjustment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of {@code GET Finance/api/DocumentAdjustments/{ap|ar}/reference?target=referencesiteforfifo} — the pending amount of a
 * sub-ledger per reference site (the API twin of the DB's reference-site pending aggregation).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceSitePending {
    private long referenceSiteCode;
    private String siteName;
    private String siteShortName;
    private BigDecimal pendingAmount;
    private BigDecimal adjustedAmount;
}
