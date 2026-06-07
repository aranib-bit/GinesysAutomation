package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Catalogue of expected UI validation error messages for negative test scenarios.
 *
 * <p>Each constant pairs a human-readable label with the exact error message text
 * the UI is expected to display after a failed save. This lets a single shared
 * behavior predicate ({@code NEGATIVE_FORM_VALIDATION_BEHAVIOR} in each
 * {@code *PredicateFactory}) handle every form-validation negative case —
 * only the data object (carrying the {@code NegativeValidation} value) changes.
 *
 * <h3>How to add a new negative scenario</h3>
 * <ol>
 *   <li>Add one new constant here with the exact error message text.</li>
 *   <li>Add one static template method in {@code ApVoucherTemplateLoader} (or
 *       {@code ArVoucherTemplateLoader}) that reuses existing builders but sets
 *       the one invalid/blank field.</li>
 *   <li>Add one enum constant to {@code ApVoucherScenarioType} (or
 *       {@code ArVoucherScenarioType}) pointing to the new template and
 *       {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR}.</li>
 *   <li>Register the new constant in the relevant {@code *FixtureFactory.SHOULD_RUN}
 *       {@code case} expression.</li>
 * </ol>
 *
 * <p><b>No changes</b> are needed in the test class, DataSource, DataSupplier,
 * or any DB validator.
 *
 * <p>This enum is shared across AR Voucher and AP Voucher negative scenarios.
 */
public enum NegativeValidation {

    // ─── Cash/Bank GL field ───────────────────────────────────────────────────

    /** Saving with no Cash/Bank GL selected. */
    BLANK_CASHBANK("Cash/Bank is required"),

    // ─── Sub-Ledger field ─────────────────────────────────────────────────────

    /** Saving with no Sub-Ledger selected. */
    BLANK_SUBLEDGER("Sub-Ledger is required"),

    // ─── Amount validation ────────────────────────────────────────────────────

    /**
     * Grid amount exceeds the total pending amount of the selected documents,
     * producing a negative outstanding balance.
     */
    AMOUNT_EXCEEDS_PENDING("Total adjustment amount cannot be in negative"),

    // ─── Reference field ──────────────────────────────────────────────────────

    /** Saving when the Reference field is mandatory but left blank. */
    BLANK_MANDATORY_REFERENCE("Reference No. is required"),

    /**
     * Reference value already exists in {@code finvchmain.refno} when the
     * scheme enforces unique references.
     */
    DUPLICATE_REFERENCE_UNIQUE("Reference No. already exists"),

    ;

    // ─────────────────────────────────────────────────────────────────────────

    private final String expectedMessage;

    NegativeValidation(String expectedMessage) {
        this.expectedMessage = expectedMessage;
    }

    /**
     * The exact error message text the UI is expected to display.
     * Used by {@code NEGATIVE_FORM_VALIDATION_BEHAVIOR} to assert the popup.
     */
    public String expectedMessage() {
        return expectedMessage;
    }
}
