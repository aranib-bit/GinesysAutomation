package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Button labels rendered inside a voucher's Sub-Ledger/Expense-Income grid rows (not the grid's
 * column headers) — e.g. "Adjust" opens the Adjustment Entry popup, "Cheque/RTGS" opens the
 * Payment Information popup. Used to build each button's own locator at the call site (see
 * {@code VoucherSubLedgerInformationBase}, {@code APVoucherTdsAndNetPayable},
 * {@code GeneralVoucherExpenseIncomeDetail}).
 */
public enum SubLedgerGridButtonLabel {
    ADJUST("Adjust"),
    CHEQUE_RTGS("Cheque/RTGS"),
    CALC_AMOUNT("Calc. Amount"),
    /** General Voucher Expense/Income Detail grid — opens the Allocate Sites popup. */
    SITE("Site");

    private final String label;

    SubLedgerGridButtonLabel(String label) { this.label = label; }

    /** The literal button text as rendered, e.g. {@code "Adjust"}. */
    public String label() { return label; }
}
