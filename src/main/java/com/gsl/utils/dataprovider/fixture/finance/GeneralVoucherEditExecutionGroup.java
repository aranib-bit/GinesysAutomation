package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Classifies each General Voucher Edit scenario into a named execution bucket — mirrors
 * {@link GeneralVoucherExecutionGroup}'s convention, but for a distinct, smaller Edit-only
 * matrix (plan §7). Gated by its own {@code gv.edit.group} parameter — deliberately NOT the
 * {@code gv.group} parameter Create/Revert use, since Edit's Mandatory/Regression membership
 * has no relationship to Create's 128-combination tiering.
 *
 * <h3>Bucket assignments (13 total: 5 MANDATORY + 8 REGRESSION_PHASE1 — see
 * {@link GeneralVoucherEditCombinations}' class Javadoc for the full numbered table)</h3>
 * <ul>
 *   <li><b>MANDATORY</b> (5): the "typical"-date header-only edit for each source status
 *       (Unposted / Posted-needs-revert — the latter also exercises the unpost-in-test step and
 *       the revert-backfill prerequisite path), the 2 negative header-date scenarios
 *       (cross-accounting-year, future-date), and the Detail-line edit on a Type1 (no Site/Sub
 *       Ledger) line, source Unposted.</li>
 *   <li><b>REGRESSION_PHASE1</b> (8): the first-valid-day/last-valid-day header-date boundary
 *       scenarios for each source status (4), plus the original Detail-line boundary scenarios —
 *       Type2 (Site-only), Type3 (Site+Sub Ledger), Is Reversal toggle, and source Posted
 *       (combines the revert-backfill prerequisite with a detail edit).</li>
 * </ul>
 */
public enum GeneralVoucherEditExecutionGroup {
    MANDATORY,
    REGRESSION_PHASE1
}
