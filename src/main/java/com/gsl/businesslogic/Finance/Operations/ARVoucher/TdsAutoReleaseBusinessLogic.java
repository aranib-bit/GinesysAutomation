package com.gsl.businesslogic.Finance.Operations.ARVoucher;

import com.gsl.enums.Finance.Operations.ARVoucher.TdsAutoReleaseAction;

/**
 * Business logic for the post-save "Auto TDS Journal will be created for this voucher.
 * Do you want to continue?" prompt on AR Voucher.
 *
 * <p><b>This is the single change point when the Auto-Release business rule changes.</b>
 * The {@link com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow Flow} and
 * {@link com.gsl.transactions.Finance.Operations.ARVoucher.TdsAutoReleaseTransaction Transaction}
 * layers contain no conditions — they only call {@link #resolve()} and execute the matching
 * step sequence.
 */
public final class TdsAutoReleaseBusinessLogic {

    /**
     * Resolves which button to click on the Auto TDS Journal prompt.
     *
     * <p><b>CHANGE POINT:</b> currently always returns {@link TdsAutoReleaseAction#CONTINUE}
     * because the testing app does not yet perform its own Auto-Release check — the prompt
     * always appears, so we always continue so the voucher saves and the document number can
     * still be extracted. When that check is added to the testing app, add a parameter here to
     * drive the {@link TdsAutoReleaseAction#NO} branch.
     */
    public static TdsAutoReleaseAction resolve() {
        // Auto-Release is a real app feature. Auto-Release check was not performed
        // in the testing app. This popup appears when auto-release was not done;
        // we always continue so the voucher saves and the document number can be extracted.
        // When the Auto-Release check is added to the testing app, add a parameter here.
        return TdsAutoReleaseAction.CONTINUE;
    }

    private TdsAutoReleaseBusinessLogic() {}
}
