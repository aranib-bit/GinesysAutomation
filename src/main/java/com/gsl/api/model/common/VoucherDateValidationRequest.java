package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST Finance/api/vouchers/ar/validations/voucherdate request body — confirmed via HAR.
 * This one endpoint uses PascalCase JSON keys (unlike the rest of the Finance API), hence the
 * explicit {@code @JsonProperty} overrides rather than relying on field-name matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherDateValidationRequest {
    /** The accounting year code being replaced/checked against — {@code 0} (kept at the
     *  HAR-observed default) for a brand-new voucher, since there is no prior year to compare. */
    @JsonProperty("OldYCODE")
    @Builder.Default
    private int oldYcode = 0;
    @JsonProperty("VoucherDate")
    private String voucherDate;
    @JsonProperty("EmpCode")
    private long empCode;
    /** {@code 0} for a new voucher; would carry the existing voucher's code when validating an edit. */
    @JsonProperty("VoucherCode")
    @Builder.Default
    private int voucherCode = 0;
    /** {@code true} for a new voucher (this client's only use case) — {@code false} would signal
     *  validating a date change on an existing one. */
    @JsonProperty("IsNewEntity")
    @Builder.Default
    private boolean isNewEntity = true;
    @JsonProperty("ConnectedOUCode")
    private long connectedOUCode;
}
