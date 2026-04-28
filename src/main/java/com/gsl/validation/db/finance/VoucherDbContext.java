package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinPost;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable context passed through all 4 DB validation levels.
 * Finance module — AR Voucher.
 *
 * Build in the test after voucher creation:
 * <pre>
 *   StateCapture capture = new StateCapture();
 *   List&lt;String&gt; invoiceNos = data.getSelectDocumentList().stream()
 *       .map(SelectDocument::getDocumentNumber).collect(Collectors.toList());
 *
 *   Map&lt;String, FinPost&gt; pre  = capture.captureInvoiceState(invoiceNos);  // BEFORE
 *   String voucherNo = page.submitAndGetVoucherNo();
 *   Map&lt;String, FinPost&gt; post = capture.captureInvoiceState(invoiceNos);  // AFTER
 *
 *   new ARVoucherDbValidator().validate(
 *       VoucherDbContext.builder()
 *           .voucherNo(voucherNo).testData(data)
 *           .preState(pre).postState(post).build());
 * </pre>
 */
@Data
@Builder
public class VoucherDbContext {

    /** UI-visible voucher number returned after creation */
    private final String voucherNo;

    /** Full test input (Excel or DB-sourced) */
    private final MergedFinanceArVoucherData testData;

    /**
     * Invoice finpost snapshot BEFORE voucher creation.
     * Key: invoice entno. Empty map for ADVANCE_PAYMENT.
     */
    @Builder.Default
    private final Map<String, FinPost> preState = Collections.emptyMap();

    /**
     * Invoice finpost snapshot AFTER voucher creation.
     * Delta (post.adjamt - pre.adjamt) must equal fintag.amount.
     */
    @Builder.Default
    private final Map<String, FinPost> postState = Collections.emptyMap();

    /**
     * Wall-clock time captured on the test machine immediately before clickSave().
     * Used by FinVchMainValidator to tolerance-check finvchmain.creation_dt.
     * Null → tolerance check is skipped (backwards-compatible with existing callers).
     */
    private final LocalDateTime saveStartTime;
}
