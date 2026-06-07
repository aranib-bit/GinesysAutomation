package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Controls which button to click on the "All Sub ledger, adjustments, TDS and payment
 * details will get cleared. Do you want to proceed?" warning popup that appears in the
 * AP Voucher document header when the On Account radio is toggled.
 *
 * <p>Driven by the "On Account Confirm" column in the DocumentInformation Excel/DB sheet.
 * Leave the column blank to default to {@link #OK_GO_AHEAD}.
 *
 * <p><b>Close behaviour:</b> clicking "Close" causes the app to revert On Account back
 * to its previous value — the attempted change is undone by the UI.
 */
public enum OnAccountClearConfirmAction {

    /** Clicks "Ok, Go Ahead" — change proceeds, sub-ledger/TDS/payment rows are cleared. */
    OK_GO_AHEAD,

    /** Clicks "Close" — app reverts On Account to its previous value (change undone by UI). */
    CLOSE;

    /**
     * Parses a raw string from Excel/DB into the corresponding action.
     * Accepts common aliases: OK/YES/OK_GO_AHEAD → {@link #OK_GO_AHEAD},
     * CLOSE/NO/CANCEL → {@link #CLOSE}.
     * Returns {@code null} when {@code value} is blank (caller treats null as {@link #OK_GO_AHEAD} default).
     *
     * @throws IllegalArgumentException for unrecognised non-blank values
     */
    public static OnAccountClearConfirmAction fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "OK", "YES", "OK_GO_AHEAD" -> OK_GO_AHEAD;
            case "CLOSE", "NO", "CANCEL"    -> CLOSE;
            default -> throw new IllegalArgumentException(
                    "Unknown OnAccountClearConfirmAction: '" + value + "'. Expected OK_GO_AHEAD or CLOSE.");
        };
    }
}
