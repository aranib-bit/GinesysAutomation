package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import com.gsl.enums.PaymentSelectionType;

/**
 * Common payment fields shared by AR and AP Voucher PaymentInformation POJOs.
 * Both are structurally identical but live in separate packages for ZeroCell
 * sheet-mapping reasons. This interface lets PaymentModeResolver and
 * FinVchDetApValidator work against a single type.
 */
public interface VoucherPaymentInfo {
    String getLineNo();
    String getTestCaseID();
    PaymentSelectionType getPaymentMode();
    String getInstrumentNo();
    String getInstrumentDate();
    String getDrawnOn();
    String getReference();
}
