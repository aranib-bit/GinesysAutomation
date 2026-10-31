package com.gsl.flow.Finance.Operations.Common;

import com.gsl.enums.AdjustmentMismatchAction;
import com.gsl.enums.AdjustmentType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.ReferenceSiteWiseFIFOPopup;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentMismatchPlan;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.AdjustmentSpec;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Log;
import org.testng.Assert;

import java.math.BigDecimal;

/**
 * The single home for driving Adjustment Entry (shared by AR and AP — GV has none): applying one
 * {@link AdjustmentSpec} by any of the three methods, reading a document's pending ceiling, and
 * answering the amount-mismatch Confirmation per an {@link AdjustmentMismatchPlan}.
 *
 * <p>Extracted from {@code APVoucherSubLedgerInformation#fillAdjustments}'s three branches so
 * Create's initial adjustment, Edit's restore/change and the "I will adjust more" additions share
 * one implementation instead of three drifting copies.
 */
public final class AdjustmentFlowSteps {

    private AdjustmentFlowSteps() {}

    /**
     * Applies {@code spec} inside an open Adjustment Entry — does NOT click OK, so the caller decides how OK is
     * answered (plain {@link AdjustmentEntry#clickOK()}, or {@link #okAnsweringMismatch}).
     *
     * <p>Opens the "Adjusted Document" menu itself, immediately before choosing the option: the floating menu
     * closes when anything runs in between (run 20261009_164937, FIN-AR-ADD-DOC-03: the popup's before-fill UI
     * checks took 15 s after the menu opened and "Select Document" was no longer interactable).
     */
    public static void applyAdjustment(AdjustmentEntry entry, AdjustmentSpec spec) {
        AdjustmentType type = spec.getType();
        entry.openAdjustedDocumentMenu();
        if (type == AdjustmentType.SELECT_DOCUMENT) {
            SelectDocumentPopup popup = entry.selectDocument();
            for (AdjustmentSpec.DocAmount d : spec.getDocs()) {
                popup.selectDocument(d.getDocumentNumber());
            }
            popup.clickSelect();
            for (AdjustmentSpec.DocAmount d : spec.getDocs()) {
                BigDecimal amount = d.getAmount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) continue;
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Adjustment amount cannot be negative for document ["
                            + d.getDocumentNumber() + "]. Got: " + amount.toPlainString());
                }
                BigDecimal ui = BigDecimalUtils.safeParse(entry.getAdjustedAmountForDocument(d.getDocumentNumber()));
                if (amount.compareTo(ui) != 0) {
                    BigDecimal pending = readPending(entry, d.getDocumentNumber());
                    boolean overPending = pending != null && pending.signum() > 0 && amount.compareTo(pending) > 0;
                    entry.setAdjustedAmountForDocument(d.getDocumentNumber(), amount.toPlainString(), overPending);
                }
            }
        } else if (type == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
            ReferenceSiteWiseFIFOPopup fifo = entry.openReferenceSiteWiseFIFOPopup();
            for (int i = 0; i < spec.getReferenceSites().size(); i++) {
                if (i > 0) fifo.addRow();
                fifo.selectReferenceSiteInCombo(spec.getReferenceSites().get(i));
            }
            fifo.clickAutoAdjust();
            var snap = entry.captureAmounts();
            Log.info("RefSiteFIFO amounts — txn={} adj={} pending={}",
                    snap.getTransactionAmount(), snap.getAdjustedAmount(), snap.getPendingToAdjust());
        } else if (type == AdjustmentType.FIFO_SETOFF) {
            entry.fifoSetOff();
        } else {
            throw new IllegalArgumentException("AdjustmentFlowSteps.applyAdjustment: unsupported type " + type);
        }
    }

    /** Pending ceiling for {@code documentNumber}, read from the Adjustment Entry grid. */
    public static BigDecimal readPending(AdjustmentEntry entry, String documentNumber) {
        return BigDecimalUtils.safeParse(entry.getPendingAmountForDocument(documentNumber));
    }

    /**
     * Clicks OK and answers the amount-mismatch Confirmation exactly as {@code plan} scripts it.
     * Empty plan ⇒ the popup must not appear. See {@link AdjustmentMismatchPlan}.
     */
    public static void okAnsweringMismatch(AdjustmentEntry entry, AdjustmentMismatchPlan plan) {
        AdjustmentEntry current = entry;
        boolean popupShowing = current.clickOkAndDetectMismatchConfirmation();
        BigDecimal txn = null;
        for (AdjustmentMismatchPlan.Step step : plan.getSteps()) {
            Assert.assertTrue(popupShowing, "[Adjustment] Expected the amount-mismatch Confirmation before step "
                    + step.getAction() + " but it did not appear (adjusted total already equals payment?).");
            DocumentPopup popup = current.mismatchConfirmation();
            if (step.getAction() == AdjustmentMismatchAction.UPDATE_PAYMENT_AMOUNT) {
                ReportLogger.business("Adjustment Confirmation → Update payment amount");
                popup.clickUpdatePaymentAmount();
                popupShowing = false;
            } else if (step.getAction() == AdjustmentMismatchAction.ADJUST_MORE) {
                ReportLogger.business("Adjustment Confirmation → I will adjust more — adding "
                        + step.getExtra().getType() + " adjustment");
                current = popup.clickAdjustMore();
                applyAdjustment(current, step.getExtra());
                popupShowing = current.clickOkAndDetectMismatchConfirmation();
            } else {
                throw new IllegalStateException("Unsupported mismatch step " + step.getAction());
            }
        }
        Assert.assertFalse(popupShowing, "[Adjustment] The amount-mismatch Confirmation appeared again after the "
                + "scripted steps finished — the plan did not bring adjusted total to payment amount.");
        if (!plan.isExpectingPopup()) {
            ReportLogger.business("Adjustment OK — no mismatch Confirmation (adjusted total == payment), as expected");
        }
    }
}
