package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Identifies which Expense/Income Detail row a detail-line Edit scenario targets, the new values
 * to apply, and (plan §4B) which of four distinct edit shapes this is.
 *
 * <p>{@link #lineNo} matches an already-existing {@code ExpenseIncomeDetail.getLineNo()} row for
 * every shape except {@link EditShape#ADD_NEW_LINE}, where it is the brand-new row's own assigned
 * {@code LineNo} (one past the last existing row — see
 * {@code DbGeneralVoucherEditDataSource.buildAddNewLine}'s Javadoc). {@link #beforeRow} is the
 * pre-edit snapshot of that row — {@code null} only for {@code ADD_NEW_LINE}, since there is no
 * "before" for a row that didn't exist yet — added so 4A's reporting can diff both sides instead
 * of only ever seeing the new values.
 */
@Getter
@Builder
public class DetailLineEditTarget {

    /** The row's {@code LineNo} — an existing row for every shape except {@code ADD_NEW_LINE},
     *  where it's the new row's own assigned LineNo. */
    private final String lineNo;

    /** Pre-edit snapshot of the row being edited — {@code null} only for {@code ADD_NEW_LINE}
     *  (no "before" exists for a row not yet on the voucher). */
    private final ExpenseIncomeDetail beforeRow;

    /** New values to apply — via {@code GeneralVoucherExpenseIncomeDetail.editRow} for
     *  {@code IN_PLACE}/{@code GL_TYPE_TRANSITION}/{@code SITE_SPLIT_ONLY}, or {@code .addRow} for
     *  {@code ADD_NEW_LINE}. For {@code SITE_SPLIT_ONLY} this is an exact copy of {@link #beforeRow}
     *  — only the site allocation below changes, not the row's own GL/amount/narration/reversal. */
    private final ExpenseIncomeDetail newValues;

    /** Which of the four edit shapes this target represents — {@code IN_PLACE} (amount/reversal/
     *  narration only, same GL/Sub-Ledger, the original and still most common shape) by default,
     *  so every call site built before this field existed needs no change. */
    @Builder.Default
    private final EditShape shape = EditShape.IN_PLACE;

    /** Pre-edit site allocations for this line — populated for a {@code SITE_SPLIT_ONLY} target
     *  (both for the diff-driving redistribution logic and for 4A's reporting); {@code null} for
     *  every other shape. */
    private final List<SiteAllocation> beforeSiteAllocations;

    /** Post-edit site allocations to apply via {@code GeneralVoucherExpenseIncomeDetail
     *  .reallocateSites} — populated only for {@code SITE_SPLIT_ONLY}; {@code null} otherwise. */
    private final List<SiteAllocation> newSiteAllocations;

    /** The four detail-line edit shapes — plan §4B. "Remove-and-re-add a line" is deliberately
     *  NOT modeled here — descoped entirely (see the plan's 4B scope-decision note) pending a
     *  live-confirmed delete-row control that doesn't exist anywhere in this codebase today. */
    public enum EditShape {
        /** Amount/reversal/narration changed on the same existing GL/Sub Ledger — the original,
         *  still most common shape (unchanged behavior from before this enum existed). */
        IN_PLACE,
        /** The line moves to a different GL of a different account class (e.g. Expense→Asset). */
        GL_TYPE_TRANSITION,
        /** A brand-new line is appended to the voucher — {@link #beforeRow} is {@code null}. */
        ADD_NEW_LINE,
        /** Same total amount/GL/narration/reversal — only the sitewise allocation split changes. */
        SITE_SPLIT_ONLY
    }
}
