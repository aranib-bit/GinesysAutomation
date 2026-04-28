package com.gsl.utils.payment;

/**
 * Represents a single payment reference scenario — valid or invalid.
 *
 * system   : payment system identifier (IMPS, UPI, NEFT, RTGS, SWIFT, ACH, SEPA)
 * value    : the generated reference string; may be null for the NULL negative scenario
 * isValid  : true for positive fixtures, false for negative / edge-case mutations
 * scenario : human-readable label used in test failure messages and ExtentReports
 */
public class PaymentReference {

    public final String system;
    public final String value;
    public final boolean isValid;
    public final String scenario;

    public PaymentReference(String system, String value, boolean isValid, String scenario) {
        this.system   = system;
        this.value    = value;
        this.isValid  = isValid;
        this.scenario = scenario;
    }

    @Override
    public String toString() {
        return "PaymentReference{system='" + system + "', scenario='" + scenario
                + "', isValid=" + isValid + ", value='" + value + "'}";
    }
}
