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

    /** Document date set beyond the latest date the scheme/period allows (e.g. a future date).
     *  <b>No longer used by General Voucher Edit</b> — confirmed live the app ACCEPTS a
     *  future-dated document within the same accounting year (see
     *  {@code VoucherEditDateVariant#FUTURE_DATE}); kept here only in case a genuine future-date
     *  rejection rule is ever confirmed for another domain/flow. */
    FUTURE_DATE_NOT_ALLOWED("Document date cannot be a future date"),

    /** Reference text longer than the {@code finvchmain.refno} column / form max length. */
    REFERENCE_TOO_LONG("Reference No. exceeds the maximum allowed length"),

    // ─── General Voucher — Allocate Sites / Sub Ledger boundary cases ─────────
    // ⚠️ UNVERIFIED text (confirmed assertion POINT, not yet confirmed literal): Save's own dialog
    // for a detail-section row error is confirmed generic — see ZERO_AMOUNT_DIALOG's reuse note
    // below — so GV_SITE_ALLOCATION_PENDING/GV_SITE_NOT_ALLOCATED are read from the row's hover
    // tooltip (GeneralVoucherExpenseIncomeDetail.rowIndicatorButton +
    // GeneralVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR's "Hover row indicator" step,
    // the same mdi-dots-horizontal/ExtJsQuickTipUtils mechanism AP/AR use for
    // ZERO_AMOUNT_TOOLTIP_STOP/AMOUNT below), never verifyErrorMessage against the dialog itself —
    // confirmed live, run 20260807_171711, that the dialog text alone was never these scenarios'
    // real message. The literal tooltip text below is still only a best-effort guess.

    /** Row's hover-tooltip text when a Type 2/Type 3 row's Allocate Sites Tagging Summary has
     *  Pending Amount != 0 at Save. Read via the row indicator, not the Save dialog — see above. */
    GV_SITE_ALLOCATION_PENDING("Site allocation is required as ledger is site applicable."),

    /** Clicking a Type 3 row's (Site AND Sub Ledger applicable) "Site" button while Sub Ledger is
     *  blank — the app blocks the button itself with its own dialog (header "No sub-ledger
     *  selected") instead of ever opening Allocate Sites, well before Save is reached. Text
     *  CONFIRMED live, run 20260807_171711 (unlike the guessed literals elsewhere in this block).
     *  UNLIKE the other two constants here, this is asserted by its own dedicated
     *  {@code GeneralVoucherPredicateFactory.NEG_SUBLEDGER_REQUIRED_BEHAVIOR} — not
     *  NEGATIVE_FORM_VALIDATION_BEHAVIOR's Save+hover flow, which this scenario never reaches. */
    GV_SUBLEDGER_REQUIRED("Please select sub-ledger."),

    /** Row's hover-tooltip text when a Type 2/Type 3 row's Site button was never clicked (no
     *  allocation attempted at all). Read via the row indicator, not the Save dialog — see above. */
    GV_SITE_NOT_ALLOCATED("Site allocation is required as ledger is site applicable."),

    // ─── General Voucher — Edit: accounting-year-aware document date ─────────
    // Asserted directly against the Save dialog via DocumentPopup.verifyErrorMessage — CONFIRMED
    // live (user-supplied screenshot): editing an existing voucher's date to fall outside its own
    // finvchmain.ycode's admyear range produces exactly this message.

    /** Edit's new document date crosses into a different accounting year than the voucher's own
     *  (finvchmain.ycode's admyear range) — the app rejects the save outright. */
    GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR(
            "The entry date cannot be changed to a new accounting year. It should belong to the same accounting year."),

    // ─── General Voucher — Create: Cash payment mode cannot be post-dated ────
    // Round 21: confirmed live (plan §Round 13) — selecting a Cash-type Cash/Bank ledger together
    // with a post-dated Document Date makes the app correctly reject the save with this exact
    // message. Asserted directly against the Save dialog via DocumentPopup.verifyErrorMessage,
    // same shape as GV_EDIT_DATE_CROSSES_ACCOUNTING_YEAR above.

    /** Create's Document Date is set in the future while the Cash/Bank ledger is Cash-type
     *  ({@code CashBankType.CASH}) — the app rejects the save outright. */
    GV_CASH_POST_DATED_NOT_ALLOWED(
            "You have selected cash account as the payment mode. Post dated entries are not allowed for cash transactions."),

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
