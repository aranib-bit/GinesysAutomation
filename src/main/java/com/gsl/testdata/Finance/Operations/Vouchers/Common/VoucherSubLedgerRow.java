package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;

/**
 * Common read contract for AP and AR sub-ledger rows.
 * Both concrete {@code SubLedgerInformation} classes extend
 * {@code SubLedgerInformationBase} (which provides {@code getSlType()}) and
 * add the fields declared here, so implementing this interface requires no
 * extra logic — only the {@code implements} clause.
 */
public interface VoucherSubLedgerRow {
    String getLineNo();
    String getSubLedger();
    String getId();
    String getSlType();
    String getGeneralLedger();
    AdjustmentType getAdjustmentType();
    PaymentSelectionType getPaymentSelection();
}
