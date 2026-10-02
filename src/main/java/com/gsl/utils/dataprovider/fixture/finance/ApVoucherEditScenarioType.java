package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Names one {@code EditApVoucher} scenario and the "apply changes" behavior that runs it — the AP
 * Edit analogue of {@code ApVoucherScenarioType} (Create's own richer, one-constant-per-combination
 * shape, not {@code GeneralVoucherEditVariant.ScenarioType}'s flatter 3-value dispatch key — this
 * dimension has real per-combination structure, closer to Create's own).
 *
 * <p><b>Only the MANDATORY-tier combinations are modeled so far</b> (Round 12's own staged rollout
 * — see the plan's combination matrix): {@link #HEADER_ONLY} (existing), {@link
 * #DETAIL_AMOUNT_ADVANCE}/{@link #DETAIL_AMOUNT_DOCUMENT} (the original motivating fix, both
 * modes), {@link #DETAIL_PAYMENT_INFO}. The remaining ~10 combinations (sub-ledger name/type
 * reselect, document-list change, multi-document redistribution, Cash/Bank-triggered refill,
 * multi-row, composed Payment+other) are each their own follow-up round once their own mechanism
 * is built and proven live — not stubbed here ahead of that, per this codebase's own "no
 * half-finished shapes" convention.
 *
 * <p><b>Why {@link #DETAIL_AMOUNT_DOCUMENT} carries a no-op "apply changes" behavior</b>: for
 * Document mode, the Amount cell is read-only (confirmed,
 * {@code ApVoucherPredicateFactory.DOC_AMOUNT_READ_ONLY_BEHAVIOR}) — the entire "change the
 * amount" mechanism lives in {@code EditApVoucher}'s own shared "Apply/Restore Sub-Ledger
 * Adjustment" step (which runs for every scenario, not just this one — see that step's own
 * Javadoc), not in a per-scenario behavior here. This constant's own execute() therefore does
 * nothing beyond satisfying the uniform dispatch shape every scenario type follows.
 */
public enum ApVoucherEditScenarioType {

    /** Reference/Remarks/Date change only — the pre-existing {@code editApVoucherHeader} scenario. */
    HEADER_ONLY(ApVoucherEditPredicateFactory.HEADER_EDIT_BEHAVIOR),

    /** Advance mode (On Account = Yes) — the row's Amount cell is a plain, directly-editable
     *  field; no adjustment/tag is involved at all. */
    DETAIL_AMOUNT_ADVANCE(ApVoucherEditPredicateFactory.PLAIN_AMOUNT_EDIT_BEHAVIOR),

    /** Document mode (On Account = No) — the row's Amount cell is read-only; the change happens
     *  entirely in the shared "Apply/Restore Sub-Ledger Adjustment" step (see class Javadoc). */
    DETAIL_AMOUNT_DOCUMENT(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR),

    /** Cheque/RTGS instrument number change only — never wired for AP Edit before this round. */
    DETAIL_PAYMENT_INFO(ApVoucherEditPredicateFactory.PAYMENT_INFO_EDIT_BEHAVIOR),

    // ── Document-mode adjustment scenarios (amount-mismatch Confirmation coverage) ─────────────
    // All use NO_OP apply-changes: the whole change is the shared "Apply/Restore Sub-Ledger
    // Adjustment" step, driven by data.getEditedDetailLine() (targets + mismatchPlan) — see
    // ApVoucherEditPredicateFactory#ADJUSTMENT_RESTORE_OR_CHANGE_BEHAVIOR.

    /** Re-tag EVERY captured document at its original amount — total == payment, so the mismatch
     *  Confirmation must NOT appear. Covers single- and multi-document tags of any entity type. */
    DETAIL_MULTI_DOC_RESTORE(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR),

    /** Lower one document's amount within its pending, leaving adjusted total below payment →
     *  Confirmation → "Update payment amount" (payment follows the new total). */
    DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR),

    /** Lower one document's amount, then "I will adjust more" — add another document (any of the
     *  3 methods) until adjusted total equals the payment again. */
    DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR),

    /** Raise one document's amount within its pending — total changes; answered per plan. */
    DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR),

    /** Exploratory negative: amount above the document's pending — records the app's behavior. */
    DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE(ApVoucherEditPredicateFactory.NO_OP_BEHAVIOR);

    private final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier;

    ApVoucherEditScenarioType(Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>> behaviorSupplier) {
        this.behaviorSupplier = behaviorSupplier;
    }

    /** True for the Document-mode scenarios whose DB validation is the adjustment invariant
     *  (payment == fintag total, tagged documents as expected) rather than a field read-back. */
    public boolean isDocumentAdjustmentScenario() {
        switch (this) {
            case DETAIL_AMOUNT_DOCUMENT:
            case DETAIL_MULTI_DOC_RESTORE:
            case DETAIL_AMOUNT_DOC_DECREASE_UPDATE_PAYMENT:
            case DETAIL_AMOUNT_DOC_DECREASE_ADJUST_MORE:
            case DETAIL_AMOUNT_DOC_INCREASE_WITHIN_PENDING:
            case DETAIL_AMOUNT_DOC_OVER_PENDING_NEGATIVE:
                return true;
            default:
                return false;
        }
    }

    /** Dispatches to this scenario type's "Apply Changes" behavior — same shape as
     *  {@code ApVoucherScenarioType#execute}/{@code GeneralVoucherEditVariant.ScenarioType#execute}. */
    public boolean execute(MergedFinanceApVoucherData data, ApVoucherAdd page) {
        return behaviorSupplier.get().test(data, page);
    }
}
