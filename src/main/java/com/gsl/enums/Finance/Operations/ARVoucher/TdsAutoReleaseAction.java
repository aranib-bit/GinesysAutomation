package com.gsl.enums.Finance.Operations.ARVoucher;

/**
 * Controls which button to click on the "Auto TDS Journal will be created for this voucher.
 * Do you want to continue?" popup ({@link
 * com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage#AUTO_TDS_JOURNAL_PROMPT})
 * that appears after saving an AR Voucher when TDS Provided = Yes and auto-release was not
 * already performed.
 *
 * <p>Resolved by {@link com.gsl.businesslogic.Finance.Operations.ARVoucher.TdsAutoReleaseBusinessLogic#resolve()}
 * and dispatched by {@link com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow#execute}
 * to the matching {@link com.gsl.transactions.Finance.Operations.ARVoucher.TdsAutoReleaseTransaction}
 * method.
 */
public enum TdsAutoReleaseAction {

    /**
     * Clicks "Continue" — the auto TDS journal is created and the flow proceeds to the
     * post-save success popup. Currently the only value {@code resolve()} ever returns: the
     * testing app does not perform its own Auto-Release check yet, so this action is always
     * taken so the voucher saves and the document number can still be extracted.
     */
    CONTINUE,

    /**
     * Clicks "No" — the auto TDS journal is not created, and no success popup follows
     * ({@link com.gsl.flow.Finance.Operations.ARVoucher.TdsAutoReleaseFlow#execute} yields
     * {@code null} for this branch). Not currently produced by {@code resolve()} — reserved
     * for when the Auto-Release check is added to the testing app.
     */
    NO
}
