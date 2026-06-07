package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Named scenario enum for the TDS Applicable Amount field in the
 * "Calculate TDS &amp; Net Payable" popup on AP Voucher (On Account = Yes).
 *
 * <p>Values are stored as strings in the Excel {@code TDS&NetPayable} sheet
 * (column "TDS Applicable Amount Behaviour") and in the DB-generated
 * {@link com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable} DTO.
 *
 * <p>Resolution at runtime:
 * <ol>
 *   <li>The page component passes the raw string to
 *       {@link com.gsl.flow.Finance.Operations.APVoucher.TdsApplicableAmountFlow}.</li>
 *   <li>The flow asks
 *       {@link com.gsl.businesslogic.Finance.Operations.APVoucher.TdsApplicableAmountBusinessLogic#resolve}
 *       which converts this string to the correct constant.</li>
 *   <li>The flow dispatches to the matching
 *       {@link com.gsl.transactions.Finance.Operations.APVoucher.TdsApplicableAmountTransaction}
 *       method.</li>
 * </ol>
 *
 * <h3>Execution group mapping</h3>
 * <ul>
 *   <li>{@link #GROSS_EQUALS_TDS}            → MANDATORY (smoke baseline)</li>
 *   <li>{@link #PARTIAL_EDIT}                → REGRESSION_PHASE1</li>
 *   <li>{@link #CLEAR_ERROR_THEN_PARTIAL_EDIT} → REGRESSION_PHASE2</li>
 * </ul>
 */
public enum TdsApplicableAmountBehaviour {

    /**
     * TDS applies to the full gross amount.
     * Gross Payable is typed; TDS Applicable Amount auto-fills to the same value
     * and is accepted as-is. Click OK.
     */
    GROSS_EQUALS_TDS,

    /**
     * TDS applies to a partial amount less than gross.
     * Gross Payable is typed; TDS Applicable Amount auto-fills; then overridden
     * to a different (smaller) value. Click OK.
     */
    PARTIAL_EDIT,

    /**
     * System validation test followed by partial edit.
     * Gross Payable is typed; TDS Applicable Amount auto-fills; field is cleared
     * entirely triggering a validation error popup; error is dismissed; field is
     * reset to gross value; then overridden to a different value. Click OK.
     */
    CLEAR_ERROR_THEN_PARTIAL_EDIT;

    /**
     * Converts an Excel/DB string value to the enum constant.
     * Null or blank → {@link #GROSS_EQUALS_TDS}, preserving backward compatibility
     * with existing Excel rows that have no value in the new column.
     *
     * @param value raw string from the data source (may be null or blank)
     * @return matching enum constant; never null
     * @throws IllegalArgumentException if the value is non-blank but does not match any constant
     */
    public static TdsApplicableAmountBehaviour fromString(String value) {
        if (value == null || value.isBlank()) return GROSS_EQUALS_TDS;
        return valueOf(value.trim().toUpperCase());
    }
}
