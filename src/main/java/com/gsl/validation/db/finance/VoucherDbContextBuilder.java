package com.gsl.validation.db.finance;

import com.gsl.model.finance.FinPost;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thread-confined accumulator for the pre/post finpost snapshot + save timestamp consumed by
 * the voucher Save sub-steps 5a–5e. Replaces the per-test juggling of {@link StateCapture} plus
 * {@code preRef}/{@code postRef}/{@code saveStartTime} array holders with four static calls:
 *
 * <pre>
 *   5a: VoucherDbContextBuilder.capturePreState(testData);   // scoped finpost snapshot BEFORE
 *   5b: VoucherDbContextBuilder.markSaveStart();             // wall-clock immediately before save
 *   5e: VoucherDbContextBuilder.capturePostState();          // scoped finpost snapshot AFTER
 *   8 : new ARVoucherDbValidator().validate(VoucherDbContextBuilder.build(docNo, testData));
 * </pre>
 *
 * <p><b>Thread-local rationale:</b> the Finance suite runs {@code parallel="methods"}; a plain
 * static field would bleed snapshots across threads. State is confined per thread and
 * {@link #build}/{@link #buildAp} call {@code TL.remove()} so a pooled TestNG thread never
 * inherits the previous test's snapshot.
 *
 * <p><b>Volume:</b> capture is scoped per-invoice via {@link StateCapture} (one
 * {@code WHERE entno = :entno} query and one row kept per invoice) — never a full-table read.
 *
 * <p><b>Extensibility (PENDING, user-side):</b> today only finpost is snapshotted. When the
 * {@code finvchmain.adjamt} / {@code finvchdet} capture lands, add the per-table snapshot here
 * (scoped via the existing keyed DAOs), carry it on the context, and add a new validator level —
 * with zero change to 5a–5e or any test.
 */
public final class VoucherDbContextBuilder {

    /** Minimal contract a voucher DTO must expose so the builder can scope the finpost capture. */
    public interface InvoiceScoped {
        /** Scoped invoice numbers (SelectDocument doc-numbers); empty when none (advance payment). */
        List<String> resolveInvoiceNos();
    }

    private static final ThreadLocal<VoucherDbContextBuilder> TL =
            ThreadLocal.withInitial(VoucherDbContextBuilder::new);

    private final StateCapture stateCapture = new StateCapture();
    private List<String> invoiceNos = Collections.emptyList();
    private Map<String, FinPost> preState  = Collections.emptyMap();
    private Map<String, FinPost> postState = Collections.emptyMap();
    private LocalDateTime saveStartTime;

    private VoucherDbContextBuilder() {}

    /**
     * 5a — derives the scoped invoice list from the DTO and snapshots only those finpost rows.
     * Throws with a clear message so the test fails at 5a rather than as an NPE in the validator.
     */
    public static void capturePreState(InvoiceScoped data) {
        VoucherDbContextBuilder b = TL.get();
        try {
            List<String> nos = data.resolveInvoiceNos();
            b.invoiceNos = nos != null ? nos : Collections.emptyList();
            b.preState   = b.stateCapture.captureInvoiceState(b.invoiceNos);
        } catch (Exception e) {
            throw new IllegalStateException("Pre-save DB capture failed", e);
        }
    }

    /** 5b — wall-clock captured on the test machine immediately before clickSave(). */
    public static void markSaveStart() {
        TL.get().saveStartTime = LocalDateTime.now();
    }

    /** 5e — re-snapshots the same scoped invoice list captured in 5a. */
    public static void capturePostState() {
        VoucherDbContextBuilder b = TL.get();
        try {
            b.postState = b.stateCapture.captureInvoiceState(b.invoiceNos);
        } catch (Exception e) {
            throw new IllegalStateException("Post-save DB capture failed", e);
        }
    }

    /** Step 8 (AR) — assembles the AR context from accumulated state and clears the thread slot. */
    public static VoucherDbContext build(String voucherNo, MergedFinanceArVoucherData data) {
        VoucherDbContextBuilder b = TL.get();
        try {
            return VoucherDbContext.builder()
                    .voucherNo(voucherNo)
                    .testData(data)
                    .preState(b.preState)
                    .postState(b.postState)
                    .saveStartTime(b.saveStartTime)
                    .build();
        } finally {
            TL.remove();
        }
    }

    /** Step 8 (AP) — assembles the AP context from accumulated state and clears the thread slot. */
    public static ApVoucherDbContext buildAp(String voucherNo, MergedFinanceApVoucherData data) {
        VoucherDbContextBuilder b = TL.get();
        try {
            return ApVoucherDbContext.builder()
                    .voucherNo(voucherNo)
                    .testData(data)
                    .preState(b.preState)
                    .postState(b.postState)
                    .saveStartTime(b.saveStartTime)
                    .build();
        } finally {
            TL.remove();
        }
    }
}
