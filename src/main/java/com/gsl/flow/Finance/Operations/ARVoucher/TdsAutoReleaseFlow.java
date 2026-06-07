package com.gsl.flow.Finance.Operations.ARVoucher;

import com.gsl.businesslogic.Finance.Operations.ARVoucher.TdsAutoReleaseBusinessLogic;
import com.gsl.enums.Finance.Operations.ARVoucher.TdsAutoReleaseAction;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.transactions.Finance.Operations.ARVoucher.TdsAutoReleaseTransaction;

public final class TdsAutoReleaseFlow {

    public static DocumentMessageBox execute(DocumentMessageBox box) {
        TdsAutoReleaseAction action = TdsAutoReleaseBusinessLogic.resolve();
        return switch (action) {
            case CONTINUE -> TdsAutoReleaseTransaction.acceptJournalCreation(box);
            case NO       -> { TdsAutoReleaseTransaction.rejectJournalCreation(box); yield null; }
        };
    }

    private TdsAutoReleaseFlow() {}
}
