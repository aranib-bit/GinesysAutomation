package com.gsl.exceptions;

public class SubLedgerNotFoundException extends RuntimeException {

    private final String expectedSlName;
    private final int rowIndex;

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

    public String getExpectedSlName() { return expectedSlName; }
    public int getRowIndex()          { return rowIndex; }
}
