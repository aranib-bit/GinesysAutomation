package com.gsl.businesslogic.Finance.Operations.APVoucher;

import com.gsl.enums.Finance.Operations.APVoucher.TdsApplicableAmountBehaviour;

import java.math.BigDecimal;

/**
 * Business logic for the TDS Applicable Amount field in the
 * "Calculate TDS &amp; Net Payable" popup on AP Voucher.
 *
 * <p><b>This is the single change point when business rules change.</b>
 * The Flow and Transaction layers contain NO conditions — they only call
 * methods defined here and execute step sequences respectively.
 *
 * <h3>When to change this class</h3>
 * <ul>
 *   <li>Minimum TDS Applicable Amount changes (e.g. from 1 to 100)
 *       → update {@link #MIN_TDS_APPLICABLE} only.</li>
 *   <li>Partial TDS is no longer allowed (must always equal gross)
 *       → {@link #isPartialEditAllowed} returns {@code false};
 *         retire {@code PARTIAL_EDIT} and {@code CLEAR_ERROR_THEN_PARTIAL_EDIT} behaviours.</li>
 *   <li>Duplicate reference numbers are disallowed
 *       → {@link #isDuplicateReferenceAllowed} returns {@code false};
 *         the predicate factory assertion flips to expect an error popup.</li>
 * </ul>
 */
public final class TdsApplicableAmountBusinessLogic {

    // ── System-enforced validation constraint ────────────────────────────────

    /**
     * Business Rule: TDS Applicable Amount must be at least this value.
     *
     * <p>The application shows an error popup when the field is cleared or set below this:
     * <em>"Accepted maximum value should not be more than 99,999,999,999.999
     * and minimum value should be not less than 1."</em>
     *
     * <p><b>CHANGE POINT:</b> If the minimum changes (e.g. business enforces min=100),
     * update only this constant — nothing in Flow, Transaction, or Page Object changes.
     */
    public static final BigDecimal MIN_TDS_APPLICABLE = BigDecimal.ONE;

    // ── Business rules (named predicates) ────────────────────────────────────

    /**
     * Rule: TDS applies to the full gross amount.
     *
     * <p>Returns {@code true} when {@code tdsApplicableAmount} is {@code null}
     * (auto-fill accepted) or equals {@code grossPayable}.
     *
     * <p><b>CHANGE POINT:</b> If the business decides TDS can never equal gross
     * (always partial), return {@code false} here →
     * {@link TdsApplicableAmountBehaviour#GROSS_EQUALS_TDS} is effectively retired.
     *
     * @param gross the Gross Payable value typed by the user
     * @param tds   the TDS Applicable Amount from test data (may be null)
     */
    public static boolean isGrossEqualsTds(BigDecimal gross, BigDecimal tds) {
        return tds == null || gross.compareTo(tds) == 0;
    }

    /**
     * Rule: TDS applies to a subset of the gross amount (partial TDS).
     *
     * <p>Returns {@code true} when {@code tdsApplicableAmount} is explicitly provided,
     * is positive, and differs from {@code grossPayable}.
     *
     * <p><b>CHANGE POINT:</b> If partial TDS is no longer allowed (must always equal
     * gross), return {@code false} here →
     * {@link TdsApplicableAmountBehaviour#PARTIAL_EDIT} and
     * {@link TdsApplicableAmountBehaviour#CLEAR_ERROR_THEN_PARTIAL_EDIT} are retired.
     *
     * @param gross the Gross Payable value typed by the user
     * @param tds   the TDS Applicable Amount from test data
     */
    public static boolean isPartialEditAllowed(BigDecimal gross, BigDecimal tds) {
        return tds != null
                && tds.compareTo(BigDecimal.ZERO) > 0
                && gross.compareTo(tds) != 0;
    }

    /**
     * Rule: Duplicate reference numbers are currently <b>ALLOWED</b> by the application.
     *
     * <p>Declared here as a named rule so that if the business makes duplicates an error,
     * the expectation in the predicate factory flips by changing only this method.
     *
     * <p><b>CHANGE POINT:</b> When duplicate reference is disallowed → return {@code false}.
     * The test predicate then asserts an error popup instead of a success document number.
     */
    public static boolean isDuplicateReferenceAllowed() {
        return true;
    }

    // ── Behaviour resolution from data ───────────────────────────────────────

    /**
     * Resolves which {@link TdsApplicableAmountBehaviour} to execute for a given TDS row.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code behaviourString} is non-blank, parse it as the enum constant
     *       (Excel/DB-specified behaviour takes precedence over data-derived resolution).</li>
     *   <li>If {@code behaviourString} is blank/null, derive from the data:
     *       {@link #isGrossEqualsTds} → {@code GROSS_EQUALS_TDS};
     *       otherwise → {@code PARTIAL_EDIT}.</li>
     * </ol>
     *
     * <p>Called by the Flow layer. All business decision logic lives here —
     * the Flow and Transaction layers contain NO conditions.
     *
     * @param behaviourString raw string from the DTO (may be null or blank)
     * @param gross           Gross Payable value from test data
     * @param tds             TDS Applicable Amount value from test data (may be null)
     * @return resolved behaviour constant; never null
     */
    public static TdsApplicableAmountBehaviour resolve(
            String behaviourString, BigDecimal gross, BigDecimal tds) {

        // Explicit value from Excel/DB takes precedence
        if (behaviourString != null && !behaviourString.isBlank()) {
            return TdsApplicableAmountBehaviour.fromString(behaviourString);
        }

        // Derive from data relationship when no explicit behaviour is set
        return isGrossEqualsTds(gross, tds)
                ? TdsApplicableAmountBehaviour.GROSS_EQUALS_TDS
                : TdsApplicableAmountBehaviour.PARTIAL_EDIT;
    }

    private TdsApplicableAmountBusinessLogic() {}
}
