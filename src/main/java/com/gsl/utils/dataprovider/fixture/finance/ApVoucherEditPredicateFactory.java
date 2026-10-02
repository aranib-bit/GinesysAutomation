package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.AdjustmentType;
import com.gsl.flow.Finance.Operations.Common.AdjustmentFlowSteps;
import com.gsl.pages.Finance.Operations.ApVoucherAdd;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.SubLedgerRowIdentity;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.CapturedTag;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherDetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.ApVoucherPaymentInfoEditTarget;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Named UI-interaction behavior constants for {@code EditApVoucher}'s scenario types — the AP
 * analogue of {@code GeneralVoucherEditPredicateFactory}, mirroring its
 * {@code Supplier<BiPredicate<Data, Page>>} shape exactly.
 *
 * <p><b>Scoped narrower than AR/GV's own equivalents, deliberately</b> — the "restore/change the
 * Sub-Ledger Adjustment" mechanism (which BOTH {@link ApVoucherEditScenarioType#HEADER_ONLY}'s
 * captured-original-tag restore AND {@link ApVoucherEditScenarioType#DETAIL_AMOUNT_DOCUMENT}'s own
 * new-amount change ultimately need) is owned entirely by {@code EditApVoucher}'s own shared
 * "Apply/Restore Sub-Ledger Adjustment" step, not by a behavior constant here — that step runs for
 * every scenario type (a no-op when nothing was captured/nothing needs restoring), so folding it
 * into just one scenario's own behavior would leave the other scenarios without it. See that
 * step's own Javadoc in {@code EditApVoucher.java} for the full resolution logic (captured-original
 * vs. data-supplied-new target).
 */
public final class ApVoucherEditPredicateFactory {

    private ApVoucherEditPredicateFactory() {}

    /** Applies the header's new date/reference/remarks — unchanged logic, moved here from what
     *  used to be {@code EditApVoucher.editApVoucherHeader}'s own inline Step 9 body. Preserves the
     *  live On Account state (read from the just-opened form) rather than trusting the data source
     *  to have guessed it correctly — toggling On Account fires a destructive "sub-ledger/TDS/
     *  payment cleared" confirmation dialog, so passing the same value back through its own
     *  equals-guard keeps this a true header-only edit. */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        HEADER_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Document Header", () -> {
                var target = data.getDocumentInformation();
                String currentOnAccount = page.getDocumentInformation().getOnAccount();
                var adjusted = new com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.DocumentInformation(
                        target.getTestCaseId(),
                        target.getOwnersite(),
                        target.getVoucherno(),
                        target.getCashbank(),
                        target.getRemarks(),
                        target.getDocumentdate(),
                        target.getReference(),
                        currentOnAccount);
                page.fillDocumentHeader(adjusted);
                ReportLogger.business("Updated date/reference/remarks via fillDocumentHeader "
                        + "(On Account preserved as '" + currentOnAccount + "')");
            });
            return true;
        };

    /** Advance mode — directly edits the row's own plain, editable Amount cell. No adjustment/tag
     *  is touched (Advance never has one, per {@code FinTag}'s own cardinality Javadoc), so the
     *  shared "Apply/Restore Sub-Ledger Adjustment" step naturally no-ops for this scenario. */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        PLAIN_AMOUNT_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Detail Line (Amount, Advance mode)", () -> {
                ApVoucherDetailLineEditTarget target = data.getEditedDetailLine();
                if (target == null) {
                    throw new IllegalStateException(
                            "[ApVoucherEditPredicateFactory] editApVoucherDetailLine (Advance mode) requires "
                            + "MergedFinanceApVoucherData.editedDetailLine to be set by the data source.");
                }
                int rowIndex = resolveRowIndex(target.getLineNo());
                page.setAmountForExistingRow(rowIndex, target.getNewAmount().toPlainString());
                ReportLogger.business("Updated Amount for LineNo='" + target.getLineNo()
                        + "' (row index " + rowIndex + ") to '" + target.getNewAmount() + "' — plain field edit");
            });
            return true;
        };

    /** Document mode — see class Javadoc for why this is a deliberate no-op: the actual amount
     *  change happens in {@code EditApVoucher}'s own shared "Apply/Restore Sub-Ledger Adjustment"
     *  step, driven by this same {@code data.getEditedDetailLine()}. */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        NO_OP_BEHAVIOR = () -> (data, page) -> true;

    /** Edits the voucher's existing Cheque/RTGS instrument number on an already-populated
     *  Sub-Ledger row — the AP counterpart to {@code GeneralVoucherEditPredicateFactory
     *  #PAYMENT_INFO_EDIT_BEHAVIOR}, reusing the exact same before/after cross-check
     *  ({@code ChequeRegisterPaymentInformationPopup#getLastObservedPreviousInstrumentNo()}) that
     *  class already proves live for GV — {@code ApPaymentInformationPopup} already extends that
     *  same class, so nothing new needed there. */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        PAYMENT_INFO_EDIT_BEHAVIOR = () -> (data, page) -> {
            StepExecutor.step("Apply Changes to Payment Information", () -> {
                ApVoucherPaymentInfoEditTarget target = data.getPaymentEditTarget();
                if (target == null) {
                    throw new IllegalStateException(
                            "[ApVoucherEditPredicateFactory] editApVoucherPaymentInfo requires "
                            + "MergedFinanceApVoucherData.paymentEditTarget to be set by the data source.");
                }
                // Row identity is read live from the UI (row 1 — every MANDATORY-tier candidate has
                // exactly one Sub-Ledger row, same convention Step 9b's own adjustment step uses),
                // not from test data — an Edit flow has no test-data-supplied row identity to
                // locate an EXISTING row by (Create always knows it upfront; Edit must discover it).
                var rowIdentity = page.readSubLedgerRowIdentity(1);
                // ApPaymentInformationPopup always extends ChequeRegisterPaymentInformationPopup for
                // this domain (see setPaymentInformationForExistingRow's own Javadoc) — no instanceof
                // check needed, unlike GV's own version which handles a page-object hierarchy where
                // that isn't statically guaranteed.
                var popup = page.setPaymentInformationForExistingRow(
                        rowIdentity.getSubLedgerName(), rowIdentity.getReferenceSite(), rowIdentity.getSlId(),
                        target.getNewPayment());
                String observedPrevious = popup.getLastObservedPreviousInstrumentNo();
                String expectedPrevious = target.getBeforePayment() != null
                        ? target.getBeforePayment().getInstrumentNo() : null;
                if (!java.util.Objects.equals(observedPrevious, expectedPrevious)) {
                    throw new IllegalStateException(
                            "[ApVoucherEditPredicateFactory] Live UI's pre-edit Instrument No. ('"
                            + observedPrevious + "') did not match the DB-resolved before-value ('"
                            + expectedPrevious + "') — the wrong voucher may have been claimed, or the "
                            + "field wasn't actually pre-filled as expected. Aborting before Save.");
                }

                ReportLogger.business("Updated Cheque Instrument No. '"
                        + (target.getBeforePayment() != null ? target.getBeforePayment().getInstrumentNo() : "?")
                        + "' → '" + target.getNewPayment().getInstrumentNo() + "' via setPaymentInformationForExistingRow");
            });
            return true;
        };

    /**
     * Shared "Apply/Restore Sub-Ledger Adjustment" step — runs for EVERY scenario type after that
     * scenario's own apply-changes behavior and before Save. Re-tags every document captured
     * before the untag step (all of them, not just one — a voucher can be tagged against several
     * documents, of any entity type), optionally overridden per document by the scenario's
     * {@link ApVoucherDetailLineEditTarget#getTargets()}, then clicks OK answering the
     * amount-mismatch Confirmation per the target's {@code mismatchPlan}. A no-op when nothing was
     * captured and no adjustment change is requested (Advance mode, or a genuinely untagged
     * Document-mode candidate). Restoring the originals keeps adjusted total == payment, so no
     * popup appears and the plan is {@code none()}.
     *
     * <p>Named as a behavior constant (rather than inline in the test class, as in Round 12) so
     * the adjustment mechanics live beside the other Edit behaviors and reuse
     * {@link AdjustmentFlowSteps} — the same code Create's {@code fillAdjustments} drives.
     */
    public static final Supplier<BiPredicate<MergedFinanceApVoucherData, ApVoucherAdd>>
        ADJUSTMENT_RESTORE_OR_CHANGE_BEHAVIOR = () -> (data, page) -> {
            java.util.List<CapturedTag> captured =
                    data.getCapturedTags() == null ? java.util.List.of() : data.getCapturedTags();
            ApVoucherDetailLineEditTarget target = data.getEditedDetailLine();
            boolean changing = target != null
                    && target.getShape() == ApVoucherDetailLineEditTarget.EditShape.ADJUSTMENT_AMOUNT_CHANGE;

            if (captured.isEmpty() && !changing) {
                ReportLogger.business("No Sub-Ledger Adjustment was captured — nothing to restore/change.");
                return true;
            }

            // Final document -> amount map: captured originals, overridden per document by the target.
            java.util.Map<String, java.math.BigDecimal> finalDocs = new java.util.LinkedHashMap<>();
            captured.forEach(c -> finalDocs.merge(c.getCounterDocNo(), c.getAmount(), java.math.BigDecimal::add));
            if (changing) {
                if (!target.getTargets().isEmpty()) {
                    target.getTargets().forEach(t -> {
                        if (t.getAmount() != null) finalDocs.put(t.getDocumentNumber(), t.getAmount());
                        else finalDocs.putIfAbsent(t.getDocumentNumber(), null);
                    });
                } else {
                    String doc = target.getTargetDocumentNumber() != null ? target.getTargetDocumentNumber()
                            : (captured.isEmpty() ? null : captured.get(0).getCounterDocNo());
                    java.math.BigDecimal amt = target.getNewAmount() != null ? target.getNewAmount()
                            : (captured.isEmpty() ? null : captured.get(0).getAmount());
                    if (doc == null) {
                        throw new IllegalStateException("[ApVoucherEditPredicateFactory] ADJUSTMENT_AMOUNT_CHANGE "
                                + "needs a target document but none was captured or supplied.");
                    }
                    finalDocs.put(doc, amt);
                }
            }
            AdjustmentMismatchPlan plan = changing ? target.mismatchPlanOrNone() : AdjustmentMismatchPlan.none();

            StepExecutor.step("Apply/Restore Sub-Ledger Adjustment", () -> {
                // Row 1 — every MANDATORY-tier candidate has exactly one Sub-Ledger row. Multi-row
                // targeting (matrix #10/#11) is follow-up work.
                SubLedgerRowIdentity rowIdentity = page.readSubLedgerRowIdentity(1);
                AdjustmentEntry entry = page.openAdjustmentEntryForExistingRow(
                        rowIdentity.getSubLedgerName(), rowIdentity.getReferenceSite(), rowIdentity.getSlId());

                AdjustmentSpec.AdjustmentSpecBuilder spec = AdjustmentSpec.builder().type(AdjustmentType.SELECT_DOCUMENT);
                finalDocs.forEach((doc, amt) -> spec.doc(new AdjustmentSpec.DocAmount(doc, amt)));
                AdjustmentFlowSteps.applyAdjustment(entry, spec.build());

                // Pending ceiling is shown per document in the grid — surface it (the column header
                // is not yet confirmed live, so a read failure must never mask the real result).
                finalDocs.forEach((doc, amt) -> {
                    try {
                        java.math.BigDecimal pending = AdjustmentFlowSteps.readPending(entry, doc);
                        if (amt != null && pending != null && amt.compareTo(pending) > 0) {
                            ReportLogger.warn("[Adjustment] Amount " + amt + " for " + doc
                                    + " exceeds its pending amount " + pending);
                        }
                    } catch (RuntimeException e) {
                        ReportLogger.warn("[Adjustment] Could not read pending amount for " + doc + ": " + e.getMessage());
                    }
                });

                AdjustmentFlowSteps.okAnsweringMismatch(entry, plan);

                StringBuilder docs = new StringBuilder();
                StringBuilder amts = new StringBuilder();
                finalDocs.forEach((d, a) -> {
                    if (docs.length() > 0) { docs.append(", "); amts.append(", "); }
                    docs.append(d);
                    amts.append(a == null ? "(default)" : a.toPlainString());
                });
                ReportLogger.business("Adjustment applied: " + (changing ? "changed" : "restored")
                        + " → documents [" + docs + "] amounts [" + amts + "]"
                        + (plan.isExpectingPopup() ? " — mismatch Confirmation answered" : ""));
                ActualResultsHolder.get().getUiAdjustedDocumentMap().put("1", docs.toString());
                ActualResultsHolder.get().getUiAdjustmentAmountMap().put("1", amts.toString());
            });
            return true;
        };

    /** Best-effort row-index mapping: {@code LineNo} is assigned 1-based, in grid-row order —
     *  mirrors {@code GeneralVoucherEditPredicateFactory#resolveRowIndex} exactly. Falls back to
     *  row 0 with a warning if {@code lineNo} isn't a plain integer. */
    private static int resolveRowIndex(String lineNo) {
        try {
            return Integer.parseInt(lineNo.trim()) - 1;
        } catch (NumberFormatException | NullPointerException e) {
            ReportLogger.warn("[ApVoucherEditPredicateFactory] Could not parse LineNo='" + lineNo
                    + "' as a row index — defaulting to row 0. " + e.getMessage());
            return 0;
        }
    }
}
