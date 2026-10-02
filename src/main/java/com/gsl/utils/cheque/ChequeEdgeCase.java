package com.gsl.utils.cheque;

/**
 * Represents a single negative / edge-case cheque number scenario.
 * Used exclusively by negative test classes — never wired into
 * UniqueChequeGenerator or the AR Voucher fixture builder.
 */
public class ChequeEdgeCase {

    /** Human-readable label for the scenario, e.g. {@code "SHORT_LENGTH"} — used in test failure messages and reports. */
    public final String scenario;
    public final String chequeNo;   // null for the NULL scenario

    public ChequeEdgeCase(String scenario, String chequeNo) {
        this.scenario = scenario;
        this.chequeNo = chequeNo;
    }

    @Override
    public String toString() {
        return "ChequeEdgeCase{scenario='" + scenario + "', chequeNo='" + chequeNo + "'}";
    }
}
