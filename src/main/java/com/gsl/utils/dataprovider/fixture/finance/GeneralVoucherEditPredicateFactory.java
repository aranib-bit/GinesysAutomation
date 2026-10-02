package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.pages.Finance.Operations.Common.ChequeRegisterPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Named UI-interaction behavior constants for {@code EditGeneralVoucher}'s two scenario types —
 * plan §4K, mirroring {@code ArVoucherPredicateFactory}'s {@code Supplier<BiPredicate<Data, Page>>}
 * shape. Replaces the inline {@code if (detailLineEdit) {...} else {...}} branch that used to
 * live directly in {@code EditGeneralVoucher.runEditFlow}'s "Apply Changes" step.
 *
 * <p><b>Scoped narrower than AR/AP's own behavior constants, deliberately.</b> AR/AP's constants
 * each own the WHOLE fill→save→snapshot-capture→validate sequence, because each scenario captures
 * its own before/after state independently, right before its own Save. GV Edit's snapshot capture
 * is structurally different: one generic, scenario-agnostic {@code DocumentContext}/BEFORE-snapshot
 * pair is resolved ONCE in {@code EditGeneralVoucher}'s shared preamble (Steps 5-6, before either
 * scenario's own step even runs), then reused by both scenario types' AFTER-capture/DB-validation
 * tail (Steps 13-14). Forcing that shared, context-dependent tail through a bare
 * {@code BiPredicate<Data, Page>} signature — the same shape AR/AP's constants use — would mean
 * either threading {@code DocumentContext}/before-snapshot refs through a signature that
 * structurally has no room for them, or duplicating the generic snapshot-capture machinery per
 * scenario for no benefit. So these two constants own only what is genuinely scenario-specific —
 * applying the header vs. detail-line change to the already-open Edit form — matching the
 * original Steps 8/8b exactly. The shared Save→Dialog→Verify→Snapshot tail (Steps 9-14) stays in
 * {@code EditGeneralVoucher.runEditFlow}, unchanged, where it already has access to
 * {@code contextRef}/{@code beforeRef}.
 */
public final class GeneralVoucherEditPredicateFactory {

    private GeneralVoucherEditPredicateFactory() {}

    /** Step 8 — apply the header's new date/reference/remarks. The data source already leaves
     *  these at the header's existing values for a DETAIL_LINE scenario, so this behavior is only
     *  ever dispatched for {@link GeneralVoucherEditVariant.ScenarioType#HEADER_ONLY}. */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        HEADER_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Document Header", () -> {
                page.fillDocumentHeader(data.getDocumentInformation());
                ReportLogger.business("Updated date/reference/remarks via fillDocumentHeader");
            });
            return true;
        };

    /** Step 8b — apply the claimed {@link DetailLineEditTarget}'s new values, branching on its
     *  {@link DetailLineEditTarget.EditShape} (plan §4B): {@code ADD_NEW_LINE} appends a fresh row
     *  via {@code addExpenseIncomeRow}; {@code SITE_SPLIT_ONLY} edits the row's own fields (a
     *  no-op — {@code newValues} is an exact copy of {@code beforeRow}, only the split changes)
     *  then re-opens the Site popup via {@code reallocateExpenseIncomeRowSites}; {@code IN_PLACE}/
     *  {@code GL_TYPE_TRANSITION} both just edit the row's fields in place — a GL-type transition
     *  needs no different page-object call, {@code editExpenseIncomeRow}/{@code fillRowBasicFields}
     *  already handle a GL change like any other field. */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        DETAIL_LINE_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Detail Line", () -> {
                DetailLineEditTarget target = data.getEditedDetailLine();
                if (target == null) {
                    throw new IllegalStateException(
                            "[GeneralVoucherEditPredicateFactory] editGeneralVoucherDetailLine requires "
                            + "MergedFinanceGeneralVoucherData.editedDetailLine to be set by the data source.");
                }
                int rowIndex = resolveRowIndex(target.getLineNo());
                switch (target.getShape()) {
                    case ADD_NEW_LINE -> {
                        page.addExpenseIncomeRow(rowIndex, target.getNewValues(), target.getNewSiteAllocations());
                        ReportLogger.business("Added new LineNo='" + target.getLineNo()
                                + "' (row index " + rowIndex + ")");
                    }
                    case SITE_SPLIT_ONLY -> {
                        page.editExpenseIncomeRow(rowIndex, target.getNewValues());
                        page.reallocateExpenseIncomeRowSites(rowIndex, target.getNewSiteAllocations());
                        ReportLogger.business("Reallocated sites for LineNo='" + target.getLineNo()
                                + "' (row index " + rowIndex + ")");
                    }
                    default -> { // IN_PLACE, GL_TYPE_TRANSITION
                        page.editExpenseIncomeRow(rowIndex, target.getNewValues());
                        ReportLogger.business("Updated LineNo='" + target.getLineNo()
                                + "' (row index " + rowIndex + ")");
                    }
                }
            });
            return true;
        };

    /** Step 8c — apply the claimed {@link PaymentInfoEditTarget}'s new instrument number to the
     *  already-open Edit form's Cash/Bank Payment popup. First live exercise of {@code
     *  GeneralVoucherAdd#handlePaymentInformation} in Edit mode — previously Create-flow-only (see
     *  {@code ChequeRegisterPaymentInformationPopup}'s class Javadoc); {@code setInstrumentNo}'s
     *  clear-before-type fix is what makes reusing it here safe against a pre-filled Instrument
     *  No. field.
     *
     *  <p>After the popup interaction, cross-checks {@code ChequeRegisterPaymentInformationPopup
     *  #getLastObservedPreviousInstrumentNo()} (the live DOM's own pre-edit value) against {@code
     *  target.getBeforePayment().getInstrumentNo()} (the value the DB-side data source resolved as
     *  "before" when it claimed this voucher) — a mismatch means the live form didn't actually
     *  hold what the DB said it should, and fails loudly here, BEFORE Save, rather than only
     *  surfacing as a confusing snapshot-diff/audit-trail failure afterward. */
    public static final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>>
        PAYMENT_INFO_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Payment Information", () -> {
                PaymentInfoEditTarget target = data.getPaymentEditTarget();
                if (target == null) {
                    throw new IllegalStateException(
                            "[GeneralVoucherEditPredicateFactory] editGeneralVoucherPaymentInfo requires "
                            + "MergedFinanceGeneralVoucherData.paymentEditTarget to be set by the data source.");
                }
                page.handlePaymentInformation(target.getNewPayment(), data.isCashBankPaymentDebit());

                var popup = page.getExpenseIncomeDetail().getLastPaymentPopup();
                if (popup instanceof ChequeRegisterPaymentInformationPopup chequePopup) {
                    String observedPrevious = chequePopup.getLastObservedPreviousInstrumentNo();
                    String expectedPrevious = target.getBeforePayment() != null
                            ? target.getBeforePayment().getInstrumentNo() : null;
                    if (!Objects.equals(observedPrevious, expectedPrevious)) {
                        throw new IllegalStateException(
                                "[GeneralVoucherEditPredicateFactory] Live UI's pre-edit Instrument No. ('"
                                + observedPrevious + "') did not match the DB-resolved before-value ('"
                                + expectedPrevious + "') — the wrong voucher may have been claimed, or the "
                                + "field wasn't actually pre-filled as expected. Aborting before Save.");
                    }
                }

                ReportLogger.business("Updated Cheque Instrument No. '"
                        + target.getBeforePayment().getInstrumentNo() + "' → '"
                        + target.getNewPayment().getInstrumentNo() + "' via handlePaymentInformation");
            });
            return true;
        };

    /** Best-effort row-index mapping: {@code LineNo} is assigned 1-based, in grid-row order, by
     *  every Edit data source (Excel and DB) — see {@code EditVoucherInfo}/
     *  {@code DbGeneralVoucherEditDataSource}'s Javadoc. Falls back to row 0 with a warning if
     *  {@code lineNo} isn't a plain integer. Moved here from {@code EditGeneralVoucher} — it's now
     *  purely an implementation detail of {@link #DETAIL_LINE_EDIT_BEHAVIOR}. */
    private static int resolveRowIndex(String lineNo) {
        try {
            return Integer.parseInt(lineNo.trim()) - 1;
        } catch (NumberFormatException | NullPointerException e) {
            ReportLogger.warn("[GeneralVoucherEditPredicateFactory] Could not parse LineNo='" + lineNo
                    + "' as a row index — defaulting to row 0. " + e.getMessage());
            return 0;
        }
    }
}
