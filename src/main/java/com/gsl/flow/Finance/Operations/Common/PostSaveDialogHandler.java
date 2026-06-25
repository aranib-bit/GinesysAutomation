package com.gsl.flow.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInfoCheckWarning;
import com.gsl.reports.ReportLogger;

import java.util.function.UnaryOperator;

/**
 * Voucher-type-aware orchestrator for the post-save popup sequence (Save sub-step 5c).
 *
 * <p>Consolidates the {@code (needsPaymentInfoWarning ? proceed : waitForMessageBox)} ternary plus
 * the optional TDS auto-journal flow that was previously inlined in every voucher Create/Edit test.
 * Each enum constant declares <em>only</em> its differences — the {@link PaymentInfoCheckWarning}
 * variant and the (AR-only) TDS decision flow; the control flow is shared.
 *
 * <p>Adding GV/Contra later is a localized change: add a constant (its warning variant + TDS flow)
 * and a {@code switch} arm in {@link #forDomain}. No test or 5a/5b/5d/5e change.
 *
 * <p>The instrument-number auto-detection and the TDS Auto-Journal <em>Continue</em> prompt are
 * already handled inside {@link DocumentPopup#waitForMessageBox()} / {@link DocumentPopup#clickProceed};
 * this handler orchestrates which entry method to call and runs the AR decision/transaction layer —
 * it does not re-implement dialog clicking.
 */
public enum PostSaveDialogHandler {

    AR(PaymentInfoCheckWarning.AR_VOUCHER, TdsAutoReleaseFlow::execute),
    AP(PaymentInfoCheckWarning.AP_VOUCHER, UnaryOperator.identity());
    // GV / CONTRA added here later — see class Javadoc.

    private final PaymentInfoCheckWarning warning;
    /** Post-message-box TDS decision step; identity when the domain has no post-save auto-journal. */
    private final UnaryOperator<DocumentMessageBox> tdsFlow;

    PostSaveDialogHandler(PaymentInfoCheckWarning warning, UnaryOperator<DocumentMessageBox> tdsFlow) {
        this.warning = warning;
        this.tdsFlow = tdsFlow;
    }

    public static PostSaveDialogHandler forDomain(VoucherDomain domain) {
        switch (domain) {
            case AR: return AR;
            case AP: return AP;
            default:
                throw new UnsupportedOperationException(
                        "No PostSaveDialogHandler configured for voucher domain " + domain);
        }
    }

    /**
     * Drives the full post-save dialog sequence and logs every dialog/action into the current
     * Extent step node (5c). Returns the success message box, or {@code null} when a decision
     * branch (e.g. TDS "No") intentionally rejects the save.
     */
    public DocumentMessageBox handle(DocumentPopup savePopup, PostSaveSpec spec) {
        DocumentMessageBox box;
        if (spec.isNeedsPaymentInfoWarning()) {
            ReportLogger.info("Post-save dialog: payment-info-check warning for " + this
                    + " (\"" + warning.getMessageSubstring() + "\") — clicking proceed");
            box = savePopup.verifyPaymentInfoCheckWarning(warning).clickProceed(warning);
        } else {
            ReportLogger.info("Post-save: no payment-info warning expected — waiting for message box");
            box = savePopup.waitForMessageBox();
        }

        if (spec.isTdsProvided()) {
            ReportLogger.info("Post-save: TDS provided — applying " + this + " post-save TDS handling");
            box = tdsFlow.apply(box);
            if (box == null) {
                ReportLogger.warn("TDS decision flow rejected journal creation — no success message box");
            }
        }
        ReportLogger.info("All post-save dialogs handled — after OK, form will close and return to "
                + this.name() + " Voucher List");
        return box;
    }

    /** Minimal contract the handler needs from a voucher's merged test-data DTO. */
    public interface PostSaveSpec {
        boolean isNeedsPaymentInfoWarning();
        boolean isTdsProvided();
    }
}
