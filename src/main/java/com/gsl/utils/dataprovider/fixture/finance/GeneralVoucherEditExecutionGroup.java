package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Classifies each General Voucher Edit scenario into a named execution bucket — mirrors
 * {@link GeneralVoucherExecutionGroup}'s convention, but for a distinct, smaller Edit-only
 * matrix (plan §7). Gated by its own {@code gv.edit.group} parameter — deliberately NOT the
 * {@code gv.group} parameter Create/Revert use, since Edit's Mandatory/Regression membership
 * has no relationship to Create's 128-combination tiering.
 *
 * <h3>Bucket assignments (30 total: 9 MANDATORY + 21 REGRESSION_PHASE1 — see
 * {@link GeneralVoucherEditCombinations}' class Javadoc for the full numbered table)</h3>
 * <ul>
 *   <li><b>MANDATORY</b> (9): the "typical"-date header-only edit for each source status
 *       (Unposted / Posted-needs-revert — the latter also exercises the unpost-in-test step and
 *       the revert-backfill prerequisite path), 2 header scenarios (cross-accounting-year
 *       negative date, and future-date against a Bank cashbank),
 *       the Detail-line edit on a Type1 (no Site/Sub Ledger) line (source Unposted) and on a
 *       shared-glcode/different-slcode pair, and all 3 Payment-Info-Edit scenarios (Cheque
 *       instrument change, Cheque&lt;-&gt;NEFT mode change both directions) — promoted here once
 *       the GV Cheque Dr/Cr bifurcation fix landed.</li>
 *   <li><b>REGRESSION_PHASE1</b> (21): the first-valid-day/last-valid-day header-date boundary
 *       scenarios for each source status (4), the header reference-only/remarks-only/date-only
 *       isolated-field scenarios (3), the negative future-date-cash scenario, the negative
 *       source-Posted-with-month-closed scenario (needs a closed-period Posted voucher in the
 *       DB, so kept out of the mandatory run), the remaining
 *       Detail-line boundary scenarios — Type2 (Site-only), Type3 (Site+Sub Ledger), Is Reversal
 *       toggle, source Posted, GL-type transition, add-new-line, and site-split-only for Type2/
 *       Type3 — and the 4 {@link PaymentInfoDateScenario} Instrument-Date-vs-entry-date
 *       scenarios (1 negative, 3 positive — see that enum's own class Javadoc).</li>
 * </ul>
 */
public enum GeneralVoucherEditExecutionGroup {
    MANDATORY,
    REGRESSION_PHASE1
}
