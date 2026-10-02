package com.gsl.transactions.Finance.Operations.ARVoucher;

import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;

/**
 * Transaction layer for the post-save "Auto TDS Journal will be created for this voucher.
 * Do you want to continue?" prompt on AR Voucher.
 *
 * <p>Each static method is the UI step sequence for one {@link
 * com.gsl.enums.Finance.Operations.ARVoucher.TdsAutoReleaseAction} branch. No business
 * decisions live here — which method runs is decided by
 * {@link com.gsl.businesslogic.Finance.Operations.ARVoucher.TdsAutoReleaseBusinessLogic} and
 * dispatched by {@link com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow#execute}.
 */
public final class TdsAutoReleaseTransaction {

    /**
     * {@code CONTINUE} branch: clicks Continue on the prompt, then waits for the post-save
     * success message box (with document number) that follows.
     */
    public static DocumentMessageBox acceptJournalCreation(DocumentMessageBox box) {
        box.verifyMessage(DocumentMessageBoxMessage.AUTO_TDS_JOURNAL_PROMPT);
        ExtentLogger.info("TDS Auto Journal prompt — clicking Continue");
        // clickContinue() waits for the TDS prompt to close
        box.clickContinue();
        // waitForMessageBox() waits for the success popup (with document number) to appear
        return new DocumentPopup().waitForMessageBox();
    }

    /**
     * {@code NO} branch: clicks No on the prompt. Journal creation is rejected and no success
     * popup follows.
     */
    public static void rejectJournalCreation(DocumentMessageBox box) {
        box.verifyMessage(DocumentMessageBoxMessage.AUTO_TDS_JOURNAL_PROMPT);
        ExtentLogger.info("TDS Auto Journal prompt — clicking No");
        box.clickNo();
    }

    private TdsAutoReleaseTransaction() {}
}
