package com.gsl.validation.snapshot.finance;

import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import com.gsl.validation.snapshot.VoucherSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Named {@link ExpectedChangeSet} builders for a future AP Voucher Edit flow — plan §2, built now
 * per explicit user decision (DB-validation wiring only — see the plan's boxed scope reminder).
 * Thin AP-labeled layer over the common {@link VoucherEditChangeSets} rules, structurally
 * identical to {@link GeneralVoucherEditChangeSets}/{@link ARVoucherEditChangeSets} — AP uses the
 * same {@code finvchdet} columns for a basic amount/reversal edit.
 *
 * <p><b>Not yet called by any test</b> — there is no {@code EditApVoucher} test class, data
 * source, or Excel sheet in this pass. See {@link ARVoucherEditChangeSets}'s class Javadoc for
 * why this takes plain primitives rather than a typed DTO, and the same open item on AP's
 * sub-ledger/TDS columns not being modeled yet.
 */
public final class APVoucherEditChangeSets {

    private APVoucherEditChangeSets() {}

    /** Header-edit change set — see {@link VoucherEditChangeSets#headerEdit}. */
    public static ExpectedChangeSet headerEdit(DocumentContext context, VoucherSnapshot beforeSnapshot,
            String newRefno, String newRemarks, LocalDate newDate) {
        return VoucherEditChangeSets.headerEdit("APVoucherEditChangeSets.headerEdit", context, beforeSnapshot,
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
        return VoucherEditChangeSets.detailLineEdit("APVoucherEditChangeSets.detailLineEdit", context, beforeSnapshot,
                newRefno, newRemarks, newDate, glcode, slcode, isDebit, newAmount, newIsReversalYes, newLineNarration);
    }
}
