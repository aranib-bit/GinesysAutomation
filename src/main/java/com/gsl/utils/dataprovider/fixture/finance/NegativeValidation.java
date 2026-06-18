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

    /** Saving with no Cash/Bank GL selected. App shows "1. Cash/Bank: This field is required." — the
     *  "1." line-number prefix is excluded; verifyErrorMessage uses contains() so it still matches. */
    BLANK_CASHBANK("Cash/Bank: This field is required."),

    /** AP Voucher: clicking Add Row in the sub-ledger grid without Cash/Bank selected. */
    BLANK_CASHBANK_ADD_ROW("Please select cash/bank ledger first."),

    // ─── Sub-Ledger field ─────────────────────────────────────────────────────

    /** AR Voucher: saving with no Sub-Ledger row filled (empty grid). */
    BLANK_SUBLEDGER_AR("There should be atleast one record in Sub Ledger Information section."),

    /** AP Voucher: saving with no Sub-Ledger row filled (empty grid). */
    BLANK_SUBLEDGER_AP("Their should be atleast one record in Sub-Ledger Detail."),

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

    // ─── Boundary "fail-beyond" messages (T-03) ───────────────────────────────
    // These pair with a *_VALID boundary scenario (at-limit, saves) and a *_INVALID
    // scenario (beyond-limit, raises one of these errors). The behaviour layer asserts
    // the popup text via DocumentPopup.verifyErrorMessage (substring/contains match).
    //
    // ⚠️ TODO(T-03): The exact UI text below is UNVERIFIED. Confirm each string against
    // the running application (trigger the case manually via the Selenium/Playwright MCP,
    // or read the app's validation resource strings) before relying on these in CI.
    // verifyErrorMessage does a contains() match, so a wrong literal silently fails the test.

    /** Error dialog shown when Save is clicked and a line has Amount = 0. */
    ZERO_AMOUNT_DIALOG("Some problems encountered in the detail section."),

    /** Inline row tooltip: blocking severity marker rendered as bold header. */
    ZERO_AMOUNT_TOOLTIP_STOP("Stop:"),

    /** Inline row tooltip: bold HTML marker — confirms Stop: is bold, not plain text. */
    ZERO_AMOUNT_TOOLTIP_STOP_BOLD("<b>Stop:</b>"),

    /** Inline row tooltip: blocking line — amount must not be zero or null. */
    ZERO_AMOUNT_TOOLTIP_AMOUNT("Amount cannot be 0 or null."),

    /** Inline tooltip (On Account No): sub-ledger row has no document adjustment performed. */
    DOC_NOT_ADJUSTED_TOOLTIP_AMOUNT(
            "Document adjustment is mandatory for non advance type payment voucher. " +
            "Adjusted amount should match with the amount of the selected sub ledger."),

    /** Information dialog: amount cell is read-only after a document has been adjusted against the SL. */
    DOC_AMOUNT_READ_ONLY(
            "Document is adjusted against the selected sub ledger, amount can not be modified manually."),

    /** Document date set beyond the latest date the scheme/period allows (e.g. a future date). */
    FUTURE_DATE_NOT_ALLOWED("Document date cannot be a future date"),

    /** Reference text longer than the {@code finvchmain.refno} column / form max length. */
    REFERENCE_TOO_LONG("Reference No. exceeds the maximum allowed length"),

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
