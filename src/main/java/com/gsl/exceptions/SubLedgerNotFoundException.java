package com.gsl.exceptions;

/**
 * Thrown by {@code JavascriptExecutorUtils.assertSubLedgerSelected(...)} when, after
 * {@code SetSubLedger} has traversed every page of the Sub-Ledger dropdown grid, the expected
 * Sub-Ledger still cannot be confirmed as selected — either no matching row was ever found during
 * the page walk, the JS-side verification of the selected row errored/returned blank, or the
 * selected row's name doesn't exactly match {@code expectedSlName}. The message is pre-formatted
 * with the {@code storeTotal} diagnostic hint (0 = data/site-context issue, &gt;0 = a name
 * mismatch) so the real cause is visible without re-running the test.
 */
public class SubLedgerNotFoundException extends RuntimeException {

    private final String expectedSlName;
    private final int rowIndex;

    /**
     * @param expectedSlName the Sub-Ledger name the test was trying to select
     * @param rowIndex       the grid row being verified when the mismatch/failure was detected
     * @param actualSlName   the Sub-Ledger name actually found in the grid, or {@code ""} when
     *                       none was found or the JS verification itself failed
     */
    public SubLedgerNotFoundException(String expectedSlName, int rowIndex, String actualSlName) {
        super(String.format(
                "SetSubLedger FAILED at row %d: Expected SL '%s' but grid shows '%s'. " +
                "SL was not found after traversing all dropdown pages. " +
                "Check console log for 'storeTotal': " +
                "if storeTotal=0 the SL is not visible in the current owner-site/OU context (data or site issue); " +
                "if storeTotal>0 the SL name does not match exactly (trim/encoding issue). " +
                "Also verify: (1) SL name matches exactly as in the application, " +
                "(2) SL is active and accessible for the current OU/site.",
                rowIndex, expectedSlName, actualSlName));
        this.expectedSlName = expectedSlName;
        this.rowIndex = rowIndex;
    }

    /** The Sub-Ledger name the test was trying to select. */
    public String getExpectedSlName() { return expectedSlName; }
    /** The grid row being verified when the mismatch/failure was detected. */
    public int getRowIndex()          { return rowIndex; }
}
