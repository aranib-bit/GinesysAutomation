package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Controls which button to click on the "Calculated TDS and Payable amount will get cleared.
 * Do you want to proceed?" confirmation popup that appears when Deduct TDS is changed
 * from Yes → No in the AP Voucher sub-ledger grid.
 *
 * <p>Driven by the "Deduct TDS Confirm" column in the SubLedgerInformation Excel/DB sheet.
 * Leave the column blank for Yes→Yes, No→No, or No→Yes changes (popup never appears).
 */
public enum DeductTdsClearConfirmAction {

    /** Clicks "Ok, Go Ahead" — TDS and Payable amounts are cleared, the change proceeds. */
    PROCEED,

    /** Clicks "Close" — confirmation is cancelled, Deduct TDS reverts to Yes. */
    CANCEL;

    /**
     * Parses a raw string from Excel/DB into the corresponding action.
     * Accepts common aliases: PROCEED/OK/YES → PROCEED, CANCEL/CLOSE/NO → CANCEL.
     * Returns {@code null} when {@code value} is blank (caller treats null as PROCEED default).
     *
     * @throws IllegalArgumentException for unrecognised non-blank values
     */
    public static DeductTdsClearConfirmAction fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "PROCEED", "OK", "YES" -> PROCEED;
            case "CANCEL",  "CLOSE", "NO" -> CANCEL;
            default -> throw new IllegalArgumentException(
                    "Unknown DeductTdsClearConfirmAction: '" + value + "'. Expected PROCEED or CANCEL.");
        };
    }
}
