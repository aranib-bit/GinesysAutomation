package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinPost;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable context passed through all 4 DB validation levels for AP Voucher.
 * Parallel to VoucherDbContext (AR Voucher) — differs only in testData type.
 */
@Data
@Builder
public class ApVoucherDbContext {

    /** UI-visible voucher number returned after creation */
    private final String voucherNo;

    /** Full test input (Excel-sourced) */
    private final MergedFinanceApVoucherData testData;

    /**
     * Invoice finpost snapshot BEFORE voucher creation.
     * Key: invoice entno. Empty map for ADVANCE_PAYMENT (On Account = Yes).
     */
    @Builder.Default
    private final Map<String, FinPost> preState = Collections.emptyMap();

    /**
     * Invoice finpost snapshot AFTER voucher creation.
     * Delta (post.adjamt - pre.adjamt) must equal fintag.amount for DOCUMENT_PAYMENT.
     */
    @Builder.Default
    private final Map<String, FinPost> postState = Collections.emptyMap();

    /**
     * Wall-clock time captured immediately before clickSave().
     * Used by FinVchMainApValidator to tolerance-check finvchmain.creation_dt.
     */
    private final LocalDateTime saveStartTime;
}
