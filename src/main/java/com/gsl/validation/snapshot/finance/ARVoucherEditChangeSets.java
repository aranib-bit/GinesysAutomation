package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Named {@link ExpectedChangeSet} builders for a future AR Voucher Edit flow — plan §2, built now
 * per explicit user decision (DB-validation wiring only — see the plan's boxed scope reminder).
 * Thin AR-labeled layer over the common {@link VoucherEditChangeSets} rules, structurally
 * identical to {@link GeneralVoucherEditChangeSets} — AR uses the same {@code finvchdet} columns
 * for a basic amount/reversal edit.
 *
 * <p><b>Not yet called by any test</b> — there is no {@code EditArVoucher} test class, data
 * source, or Excel sheet in this pass. Takes plain primitives rather than a typed
 * {@code DocumentInformation}/{@code ExpenseIncomeDetail}-equivalent DTO since AR's own Edit
 * data-source shape doesn't exist yet; whichever future AR Edit data source is built can either
 * call this directly or wrap it with its own thin adapter, the same way
 * {@code GeneralVoucherEditChangeSets} adapts its own {@code DocumentInformation}/
 * {@code ExpenseIncomeDetail} types today.
 *
 * <p><b>Open item</b> (same class of uncertainty already flagged for GV's own {@code finvchdet}
 * row-matching): AR's sub-ledger/TDS columns are not modeled here — only the common base's
 * columns plus {@code finvchdet}'s {@code damount}/{@code camount}/{@code isreversal}. If an AR
 * Edit scenario ever touches a sub-ledger row's own columns, this class will need its own
 * additional overrides at that point.
 */
public final class ARVoucherEditChangeSets {

    private ARVoucherEditChangeSets() {}

    /** Header-edit change set — see {@link VoucherEditChangeSets#headerEdit}. */
    public static ExpectedChangeSet headerEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            String newRefno, String newRemarks, LocalDate newDate) {
        return VoucherEditChangeSets.headerEdit("ARVoucherEditChangeSets.headerEdit", context, beforeSnapshot,
                newRefno, newRemarks, newDate);
    }

    /** Detail-line-edit change set — see {@link VoucherEditChangeSets#detailLineEdit}. {@code
     *  slcode} disambiguates two sub-ledger-applicable lines sharing the same {@code glcode} —
     *  pass {@code 0} for a line with no sub-ledger. {@code newLineNarration} is the edited
     *  line's own narration (e.g. {@code SubLedgerInformation.ledgerNarration}) — pass
     *  {@code null} when not editing/asserting a per-line narration. */
    public static ExpectedChangeSet detailLineEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            String newRefno, String newRemarks, LocalDate newDate,
            long glcode, long slcode, boolean isDebit, BigDecimal newAmount, boolean newIsReversalYes,
            String newLineNarration) {
        return VoucherEditChangeSets.detailLineEdit("ARVoucherEditChangeSets.detailLineEdit", context, beforeSnapshot,
                newRefno, newRemarks, newDate, glcode, slcode, isDebit, newAmount, newIsReversalYes, newLineNarration);
    }
}
