package com.gsl.enums;

/**
 * What a scenario does when Adjustment Entry's OK raises the amount-mismatch Confirmation
 * ("Document adjustment is mandatory for non-advance type payment voucher. Adjusted amount should
 * be equal to payment amount of current record.") because the adjusted total differs from the
 * row's payment amount — confirmed live in both AP Add and Edit.
 */
public enum AdjustmentMismatchAction {

    /** No mismatch is expected (adjusted total == payment amount); the popup must NOT appear. */
    NONE,

    /** Click "Update payment amount" — the row's payment amount becomes the adjusted total. */
    UPDATE_PAYMENT_AMOUNT,

    /** Click "I will adjust more" — stay in Adjustment Entry and add further documents. */
    ADJUST_MORE
}
