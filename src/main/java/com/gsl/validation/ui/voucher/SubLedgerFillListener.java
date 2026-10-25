package com.gsl.validation.ui.voucher;

import com.gsl.enums.AdjustmentType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;

/**
 * Observer of the BUSINESS fill of a voucher's sub-ledger grid (AP / AR). The fill code announces what it just
 * did; a recorder validates the form while the relevant state exists (a row just filled, a popup still open)
 * without the fill code knowing anything about validation and without extra clicks or reopened popups.
 * Implementations must never throw into the fill — the hooks are wrapped, a failure is logged and skipped.
 */
public interface SubLedgerFillListener {

    /** Grid row {@code rowIndex} (0-based) has just had its Sub Ledger / Reference Site / amount filled. */
    default void onRowFilled(int rowIndex) {}

    /** The Adjustment Entry popup of the row is open and nothing has been added yet. */
    default void onAdjustmentOpened(int rowIndex, AdjustmentType type) {}

    /** The adjustment has been applied (documents are in the popup grid) and OK has not been clicked yet. */
    default void onAdjustmentApplied(int rowIndex, AdjustmentType type) {}

    /** The Payment Information popup of the row is open and empty. */
    default void onPaymentOpened(int rowIndex, PaymentInformationPopup popup) {}

    /** The Payment Information popup is filled and OK has not been clicked yet. */
    default void onPaymentFilled(int rowIndex, PaymentInformationPopup popup, VoucherPaymentInfo payment) {}
}
