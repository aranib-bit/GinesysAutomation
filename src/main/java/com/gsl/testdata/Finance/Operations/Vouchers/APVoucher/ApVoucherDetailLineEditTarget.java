package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.math.BigDecimal;
import java.util.List;

/**
 * Identifies which Sub-Ledger row an {@code editApVoucherDetailLine} scenario targets and the new
 * amount(s) to apply — the AP analogue of General Voucher's own {@code DetailLineEditTarget}. AP
 * Voucher's row-shape is simpler than GV's Expense/Income Detail rows: the target is always the
 * row's amount, and the mechanism differs sharply by mode:
 *
 * <ul>
 *   <li>{@link EditShape#AMOUNT_ONLY} (Advance, On Account = Yes) — the row's Amount cell is a
 *       plain, directly-editable field; no adjustment/tag is involved at all ({@code fintag}
 *       never applies to an Advance-mode row).</li>
 *   <li>{@link EditShape#ADJUSTMENT_AMOUNT_CHANGE} (Document, On Account = No) — the row's Amount
 *       cell is genuinely read-only (confirmed live,
 *       {@code ApVoucherPredicateFactory.DOC_AMOUNT_READ_ONLY_BEHAVIOR}); the only way to change
 *       it is to re-open the Document Adjustment ("Adjust") popup. Targets are expressed as
 *       per-document overrides ({@link #targets}, by counter-document number — documents captured
 *       from the live tags but not listed here are restored at their original amounts), or, for
 *       the original single-document shape, {@link #targetDocumentNumber}/{@link #newAmount}.
 *       When the resulting adjusted total differs from the row's payment amount, Adjustment Entry's
 *       OK raises the amount-mismatch Confirmation, answered per {@link #mismatchPlan}.</li>
 * </ul>
 */
@Getter
@Builder
public class ApVoucherDetailLineEditTarget {

    /** The row's {@code LineNo} — matches an already-existing sub-ledger row on the claimed voucher. */
    private final String lineNo;

    /** Legacy single-document shape: the new amount to apply — via a plain field edit
     *  ({@code AMOUNT_ONLY}) or via the Adjustment popup ({@code ADJUSTMENT_AMOUNT_CHANGE}). */
    private final BigDecimal newAmount;

    /** Legacy single-document shape: counter-document to adjust against; {@code null} = the first
     *  captured tag's document (the "default same" convention). */
    private final String targetDocumentNumber;

    /** Which edit shape this target represents. */
    @Builder.Default
    private final EditShape shape = EditShape.AMOUNT_ONLY;

    /** Multi-document shape: per-document amount overrides (an entry whose document is not among
     *  the captured tags is added as a new document). Empty = use the legacy fields. */
    @Singular("docTarget")
    private final List<AdjustmentSpec.DocAmount> targets;

    /** How the amount-mismatch Confirmation is answered; {@code null} = {@link AdjustmentMismatchPlan#none()}. */
    private final AdjustmentMismatchPlan mismatchPlan;

    /** Payment amount the saved row is expected to carry after the edit; {@code null} = unchanged. */
    private final BigDecimal expectedPaymentAmount;

    public AdjustmentMismatchPlan mismatchPlanOrNone() {
        return mismatchPlan != null ? mismatchPlan : AdjustmentMismatchPlan.none();
    }

    public enum EditShape {
        /** Advance mode — plain, directly-editable Amount field. No adjustment/tag involved. */
        AMOUNT_ONLY,
        /** Document mode — Amount is read-only; changed via the Adjustment popup instead. */
        ADJUSTMENT_AMOUNT_CHANGE
    }
}
