package com.gsl.businesslogic.Finance.Operations.ARVoucher;

import com.gsl.enums.Finance.Operations.ARVoucher.TdsAutoReleaseAction;

public final class TdsAutoReleaseBusinessLogic {

    public static TdsAutoReleaseAction resolve() {
        // Auto-Release is a real app feature. Auto-Release check was not performed
        // in the testing app. This popup appears when auto-release was not done;
        // we always continue so the voucher saves and the document number can be extracted.
        // When the Auto-Release check is added to the testing app, add a parameter here.
        return TdsAutoReleaseAction.CONTINUE;
    }

    private TdsAutoReleaseBusinessLogic() {}
}
