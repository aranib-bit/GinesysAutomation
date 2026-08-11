package com.gsl.pages.enums.Finance.Operations.Common;

public enum SubLedgerGridButtonLabel {
    ADJUST("Adjust"),
    CHEQUE_RTGS("Cheque/RTGS"),
    CALC_AMOUNT("Calc. Amount"),
    /** General Voucher Expense/Income Detail grid — opens the Allocate Sites popup. */
    SITE("Site");

    private final String label;

    SubLedgerGridButtonLabel(String label) { this.label = label; }

    public String label() { return label; }
}
