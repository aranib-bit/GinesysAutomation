package com.gsl.api.model.arvoucher;

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
    @JsonProperty("OldYCODE")
    @Builder.Default
    private int oldYcode = 0;
    @JsonProperty("VoucherDate")
    private String voucherDate;
    @JsonProperty("EmpCode")
    private long empCode;
    @JsonProperty("VoucherCode")
    @Builder.Default
    private int voucherCode = 0;
    @JsonProperty("IsNewEntity")
    @Builder.Default
    private boolean isNewEntity = true;
    @JsonProperty("ConnectedOUCode")
    private long connectedOUCode;
}
