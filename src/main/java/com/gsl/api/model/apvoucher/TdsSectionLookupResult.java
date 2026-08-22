package com.gsl.api.model.apvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET Finance/api/tdssection/tdsname item shape — confirmed via HAR
 * (CreateAPVoucherCustomerAdvanceTDS_stage-ideal.ginesys.cloud4.har). TDS-only lookup: populated
 * once the "Deduct TDS" checkbox is ticked for a voucher line, scoped to the target sub-ledger via
 * {@code slCode}. Note: applicability itself (should TDS be deducted at all) is not carried here —
 * that comes from {@code finsl.tds_app} in the DB — this call only lists the *choices* once you
 * already know TDS applies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TdsSectionLookupResult {
    private long code;
    private String tdsName;
    private String section;
    private int tdsSource;
    private String tdsSourceName;
    private double tdsRate;
    private long tdsAgencyCode;
    private String tdsAgencyName;
    private String tdsSourceType;
    private String tdsSourceTypeDisplay;
}
