package com.gsl.transactions.Finance.Operations.ARVoucher;

import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;

public final class TdsAutoReleaseTransaction {

    public static DocumentMessageBox acceptJournalCreation(DocumentMessageBox box) {
        box.verifyMessage(DocumentMessageBoxMessage.AUTO_TDS_JOURNAL_PROMPT);
        ExtentLogger.info("TDS Auto Journal prompt — clicking Continue");
        // clickContinue() waits for the TDS prompt to close
        box.clickContinue();
        // waitForMessageBox() waits for the success popup (with document number) to appear
        return new DocumentPopup().waitForMessageBox();
    }

    public static void rejectJournalCreation(DocumentMessageBox box) {
        box.verifyMessage(DocumentMessageBoxMessage.AUTO_TDS_JOURNAL_PROMPT);
        ExtentLogger.info("TDS Auto Journal prompt — clicking No");
        box.clickNo();
    }

    private TdsAutoReleaseTransaction() {}
}
