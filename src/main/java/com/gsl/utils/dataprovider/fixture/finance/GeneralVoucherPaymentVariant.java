package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.enums.PaymentSelectionType;

/**
 * Payment-mode dimension for General Voucher's Cash/Bank counter-leg — mirrors
 * {@link SelectDocumentVariant.PaymentMode} but scoped to GV's single counter-leg
 * row (no {@code MIX}: AP/AR alternate payment mode across multiple sub-ledger
 * lines, GV has only one counter-leg row per voucher).
 *
 * <p><b>Automation status:</b> a best-effort {@code GvPaymentInformationPopup} driver now
 * exists (mirroring AP/AR's {@code PaymentInformationPopup} Template Method, triggered via
 * {@code GeneralVoucherExpenseIncomeDetail.handlePaymentInformation}), so {@link #CHEQUE}/
 * {@link #NEFT_RTGS} are no longer blocked in {@code DbGeneralVoucherDataSource
 * .loadFreshForScenario} (see {@link #isBlockedPendingUiDiscovery()}). <b>UNVERIFIED —</b>
 * the Payment button locator and popup field locators have never been confirmed against the
 * live UI; expect to fix one of them after the first real run against these variants. {@link
 * #NONE} and {@link #CASH} require no fill-in interaction and were already fully automatable.
 */
public enum GeneralVoucherPaymentVariant {

    /** Bank leg, Cheque payment mode with instrument no/date — BLOCKED, see class Javadoc. */
    CHEQUE,
    /** Bank leg, NEFT/RTGS payment mode with instrument no/date — BLOCKED, see class Javadoc. */
    NEFT_RTGS,
    /** Bank leg, payment info deliberately skipped — triggers the post-save instrument-missing warning. */
    NONE,
    /** Cash leg — no instrument number required at all. */
    CASH;

    /** The counter-leg GL type this variant implies. */
    public CashBankType cashBankType() {
        return this == CASH ? CashBankType.CASH : CashBankType.BANK;
    }

    /** {@code true} only for {@link #NONE} — the app's post-save "instrument number missing" warning. */
    public boolean needsPaymentInfoWarning() {
        return this == NONE;
    }

    /**
     * {@code false} for all variants — a best-effort {@code GvPaymentInformationPopup} driver
     * now exists for {@link #CHEQUE}/{@link #NEFT_RTGS}. See class Javadoc: its locators are
     * unverified against the live UI, so re-enable this guard (return {@code true} for those
     * two) if the first live run shows the driver needs more discovery work before it's usable.
     */
    public boolean isBlockedPendingUiDiscovery() {
        return false;
    }

    /**
     * Expected {@code finvchdet.payment_mode} code for DB validation. {@code null} for
     * {@link #NONE} — what the app actually stores when payment info is skipped is an open
     * item (see plan's Verification/Open items) and must be confirmed before asserting it.
     */
    public String expectedDbPaymentModeCode() {
        return switch (this) {
            case CHEQUE -> PaymentSelectionType.CHEQUE.dbCode();
            case NEFT_RTGS -> PaymentSelectionType.NEFT_RTGS.dbCode();
            case CASH -> PaymentSelectionType.CASH.dbCode();
            case NONE -> null;
        };
    }
}
