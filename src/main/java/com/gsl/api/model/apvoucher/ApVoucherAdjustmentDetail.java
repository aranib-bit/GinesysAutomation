package com.gsl.api.model.apvoucher;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One document adjusted by an AP Voucher line ({@code apVoucherAdjustmentDetail[]} of {@code POST Finance/api/vouchers/ap}), captured via
 * HAR (APVoucherwithalllookupsDocAdjustment). {@code documentType} is the pending document's {@code entype} verbatim
 * ({@code DocumentAdjustments/apvoucher} items carry it), so it needs no mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApVoucherAdjustmentDetail {
    /** The adjusted document's {@code entCode}. */
    private long docCode;
    private String documentType;
    /** The document's own amount. */
    private double actualAmount;
    /** What this line adjusts against it. */
    private double adjustedAmount;
    @Builder.Default
    private boolean cashDiscountApplied = false;
    @Builder.Default
    private double cashDiscountFactor = 0;
    @Builder.Default
    private double cashDiscountApplicableAmount = 0;
    @Builder.Default
    private double cashDiscountAmount = 0;
    /** Equals {@code adjustedAmount} when no cash discount applies. */
    private double invoiceAdjustedAmountAfterDiscount;
}
