package com.gsl.flow.Finance.Operations.ARVoucher;

import com.gsl.businesslogic.Finance.Operations.ARVoucher.TdsAutoReleaseBusinessLogic;
import com.gsl.enums.Finance.Operations.ARVoucher.TdsAutoReleaseAction;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.transactions.Finance.Operations.ARVoucher.TdsAutoReleaseTransaction;

/**
 * Flow layer for the post-save "Auto TDS Journal will be created for this voucher.
 * Do you want to continue?" prompt on AR Voucher.
 *
 * <p>Asks the business logic layer WHAT to do ({@link TdsAutoReleaseBusinessLogic#resolve()})
 * and dispatches to the matching transaction method — this class holds no conditions itself.
 * Invoked as the AR TDS decision step from
 * {@link com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler#AR}.
 */
public final class TdsAutoReleaseFlow {

    /**
     * Resolves the Auto-Release action and dispatches to the matching transaction method.
     *
     * @param box the Auto TDS Journal prompt message box
     * @return the success message box when Continue is clicked; {@code null} when No is clicked
     *         (journal creation rejected, no success popup follows)
     */
    public static DocumentMessageBox execute(DocumentMessageBox box) {
        TdsAutoReleaseAction action = TdsAutoReleaseBusinessLogic.resolve();
        return switch (action) {
            case CONTINUE -> TdsAutoReleaseTransaction.acceptJournalCreation(box);
            case NO       -> { TdsAutoReleaseTransaction.rejectJournalCreation(box); yield null; }
        };
    }

    private TdsAutoReleaseFlow() {}
}
