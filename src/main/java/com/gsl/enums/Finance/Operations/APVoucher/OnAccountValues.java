package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Yes/No display values for the AP Voucher header's "On Account" field — labels the boolean
 * {@code onAccount} flag as its literal UI text for report rendering, e.g. in
 * {@link com.gsl.reports.ActualResultsLogger} (the "Flags" sub-section's expected-vs-actual row).
 *
 * <p>This is a report-formatting enum only: it renders the same underlying flag that
 * {@link OnAccountMode} models functionally (which sub-ledger grid columns are present in the
 * DOM). Neither enum derives the other; each is the source of truth for its own concern.
 */
public enum OnAccountValues {

    /** On Account = Yes (advance payment). */
    YES("Yes"),
    /** On Account = No (document adjustment). */
    NO("No");

    private final String label;

    OnAccountValues(String label) { this.label = label; }

    /** Returns the literal UI label for this value ("Yes" or "No"). */
    public String getLabel() { return label; }

    /** Converts the domain boolean flag to its display value ({@code true} &rarr; {@link #YES}). */
    public static OnAccountValues from(boolean b) { return b ? YES : NO; }
}
