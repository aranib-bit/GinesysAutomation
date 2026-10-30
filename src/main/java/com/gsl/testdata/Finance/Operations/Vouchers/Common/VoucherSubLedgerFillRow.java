package com.gsl.testdata.Finance.Operations.Vouchers.Common;

/**
 * A {@link VoucherSubLedgerRow} the UI fill flow drives (AP and AR rows): adds the Reference Site pass and test-case pairing the fill
 * needs. General Voucher's reporting adapter deliberately stays a plain {@link VoucherSubLedgerRow}.
 */
public interface VoucherSubLedgerFillRow extends VoucherSubLedgerRow {
    String getTestCaseID();
    String getReferenceSite();
    void setReferenceSite(String referenceSite);
    /** True when Pass 2 of the fill must overwrite the Reference Site (see {@code SubLedgerInformationBase}). */
    boolean isChangeReferenceSite();
}
