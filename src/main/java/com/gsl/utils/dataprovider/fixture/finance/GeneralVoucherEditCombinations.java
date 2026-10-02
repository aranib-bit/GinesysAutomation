package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the 30-scenario General Voucher Edit matrix (originally a fixed 7 — plan §7's
 * table — expanded with accounting-year-aware header date variants, see the "GV Edit:
 * accounting-year-aware dates..." plan; #18 (formerly #14/#17) added to verify the "finpost
 * narration change detection" fix — see the "Fix finpost/finvchdet narration-change detection"
 * plan; #10-12 added for field-isolated header edits — see plan §4D; #19-21 added for the new
 * detail-line edit shapes — see plan §4B; #23 added for the closed-accounting-period negative
 * scenario — see the "GV Revert picks documents in a now-closed accounting period, +
 * closed-period negative-test coverage" plan; #8/#9 split from a single, ambiguous #8 — see
 * "Round 22" of the GV plan, FIN-GV-EDIT-HEADER-04: the app's own post-dated-document rule
 * differs by the claimed voucher's Cash/Bank ledger type, so the old single variant's pass/fail
 * meaning depended on pure luck of which candidate got claimed; #24 added for the new
 * Payment-Info edit scenario — see the "Payment Info Edit" plan; #25/#26 added for that same
 * plan's payment-MODE-change extension, Cheque↔NEFT/RTGS; #27-30 added for {@link
 * PaymentInfoDateScenario} — Instrument Date vs. entry-date validation across the Dr/Cr ×
 * Cheque/NEFT/RTGS combinations that are actually reachable via the live UI, see that enum's own
 * class Javadoc for the full combination analysis and why Dr+NEFT/RTGS is excluded):
 *
 * <table>
 *     <caption>Edit scenario matrix</caption>
 *     <tr><th>#</th><th>Group</th><th>Scenario</th></tr>
 *     <tr><td>1</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, date=first valid day of accounting year</td></tr>
 *     <tr><td>2</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, date=last valid day of accounting year</td></tr>
 *     <tr><td>3</td><td>MANDATORY</td><td>Header-only edit, source Unposted, date=random day (capped at today)</td></tr>
 *     <tr><td>4</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Posted (revert-backfill), date=first valid day</td></tr>
 *     <tr><td>5</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Posted (revert-backfill), date=last valid day</td></tr>
 *     <tr><td>6</td><td>MANDATORY</td><td>Header-only edit, source Posted (revert-backfill), date=random day (capped at today)</td></tr>
 *     <tr><td>7</td><td>MANDATORY</td><td>Negative: header-only edit, date crosses into a different accounting year — expects rejection</td></tr>
 *     <tr><td>8</td><td>MANDATORY</td><td>Positive: header-only edit, date is in the future (same accounting year), claimed candidate's
 *         Cash/Bank ledger is Bank-type — app confirmed to accept future-dated documents for Bank-type</td></tr>
 *     <tr><td>9</td><td>REGRESSION_PHASE1</td><td>Negative: header-only edit, date is in the future (same accounting year), claimed candidate's
 *         Cash/Bank ledger is Cash-type — app rejects post-dated documents for Cash-type ("Post dated entries
 *         are not allowed for cash transactions")</td></tr>
 *     <tr><td>10</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=REFERENCE_ONLY — only Reference changes, Remarks/Date held unchanged</td></tr>
 *     <tr><td>11</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=REMARKS_ONLY — only Remarks changes, Reference/Date held unchanged</td></tr>
 *     <tr><td>12</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=DATE_ONLY — only Date changes, Reference/Remarks held unchanged</td></tr>
 *     <tr><td>13</td><td>MANDATORY</td><td>Detail-line edit, Type1, source Unposted</td></tr>
 *     <tr><td>14</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, Type2, source Unposted</td></tr>
 *     <tr><td>15</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, Type3, source Unposted</td></tr>
 *     <tr><td>16</td><td>REGRESSION_PHASE1</td><td>Detail-line edit toggling Is Reversal, source Unposted</td></tr>
 *     <tr><td>17</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, source Posted (revert-backfill)</td></tr>
 *     <tr><td>18</td><td>MANDATORY</td><td>Detail-line edit, source Unposted, editing Ledger Narration on a
 *         line whose glcode is shared with a sibling line of a different slcode — regression guard for the
 *         finpost/finvchdet narration-change ambiguity fix (confirmed live: real rows share a glcode with
 *         different slcode and narration; live data shows this only on {@code fingl.slapp='Y'}/
 *         {@code costapp='N'} GL accounts — the unmodeled 4th {@code GlLineType} combination, hence no
 *         {@code glLineType} constraint on this variant)</td></tr>
 *     <tr><td>19</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, GL-type transition (Type1 line moves to
 *         a different, unused Asset-class GL), source Unposted</td></tr>
 *     <tr><td>20</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, add a brand-new Type1 line, source Unposted</td></tr>
 *     <tr><td>21</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, site-split-only (Type2), source Unposted —
 *         same total amount, sitewise allocation redistributed</td></tr>
 *     <tr><td>22</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, site-split-only (Type3), source Unposted —
 *         same total amount, sitewise allocation redistributed</td></tr>
 *     <tr><td>23</td><td>MANDATORY</td><td>Negative: header-only, source Posted, claimed candidate's own
 *         accounting year/financial month is already closed — expects the live app to refuse even the
 *         "Unpost if Posted" prep step (never reaches Save)</td></tr>
 *     <tr><td>24</td><td>REGRESSION_PHASE1</td><td>Payment-Info edit, source Unposted, claimed candidate's
 *         Cash/Bank ledger is Bank-type with an existing Cheque instrument no. — changes it to a fresh
 *         known/same-bank one (first live exercise of the Cash/Bank Payment popup in Edit mode)</td></tr>
 *     <tr><td>25</td><td>REGRESSION_PHASE1</td><td>Payment-Info edit, source Unposted, claimed candidate's
 *         first finvchdet row is Cheque-mode — changes payment MODE to NEFT/RTGS (a fresh synthetic
 *         reference)</td></tr>
 *     <tr><td>26</td><td>REGRESSION_PHASE1</td><td>Payment-Info edit, source Unposted, claimed candidate's
 *         first finvchdet row is NEFT/RTGS-mode — changes payment MODE to Cheque (a fresh known/same-bank
 *         instrument no.)</td></tr>
 *     <tr><td>27</td><td>REGRESSION_PHASE1</td><td>Negative: Payment-Info edit, Cr-side counter-leg,
 *         final mode NEFT/RTGS, Instrument Date set after the voucher's own entry date — app rejects the
 *         save ("Instrument date cannot be greater than entry date")</td></tr>
 *     <tr><td>28</td><td>REGRESSION_PHASE1</td><td>Positive: Payment-Info edit, Cr-side counter-leg,
 *         final mode NEFT/RTGS, Instrument Date left at the voucher's own entry date — baseline confirming
 *         the Cr-side NEFT/RTGS path itself succeeds, independent of #27's rejection rule</td></tr>
 *     <tr><td>29</td><td>REGRESSION_PHASE1</td><td>Positive: Payment-Info edit, Cr-side counter-leg,
 *         final mode Cheque, Instrument Date set after the voucher's own entry date — confirms Cheque,
 *         unlike NEFT/RTGS, allows a post-dated instrument</td></tr>
 *     <tr><td>30</td><td>REGRESSION_PHASE1</td><td>Positive: Payment-Info edit, Dr-side counter-leg,
 *         final mode Cheque, Instrument Date set after the voucher's own entry date — same rule as #29,
 *         exercised through the other popup implementation (GvPaymentInformationPopup's known-cheque
 *         branch, not GvReceiptPaymentInformationPopup)</td></tr>
 * </table>
 *
 * Unlike {@link GeneralVoucherCombinations}, this is a small, fixed, hand-enumerated list —
 * Edit's matrix doesn't cross a large Cartesian product the way Create's does. The header date
 * variants ARE a small deliberate cross (source-status × date-variant) — 6 positive
 * combinations, not the full theoretical space, plus 2 hand-picked negative cases.
 *
 * <p><b>Plan §9.5 — entry method:</b> each variant still gets a single
 * {@link VoucherEditEntryMethod}, ALTERNATING through the two values in this declaration order —
 * computed via {@link VoucherEditEntryMethod#forIndex(int)} (a shared, domain-agnostic helper —
 * not GV-specific — so AR/AP's own future Edit-combination classes reuse the identical
 * alternation rule) in a final pass over the built list, rather than hand-written as a literal in
 * each builder chain — so both live UI entry paths are exercised across the scenarios without
 * duplicating any of them.
 */
public final class GeneralVoucherEditCombinations {

    private GeneralVoucherEditCombinations() {}

    /**
     * Builds the fixed 30-scenario Edit matrix (see this class's own Javadoc for the full
     * numbered table), then makes a final pass over the built list to alternate each variant's
     * {@link VoucherEditEntryMethod} (plan §9.5) — hand-enumerated, not a Cartesian product like
     * {@link GeneralVoucherCombinations#allCombinations()}.
     */
    public static List<GeneralVoucherEditVariant> allCombinations() {
        List<GeneralVoucherEditVariant> out = new ArrayList<>();

        // ── Positive header scenarios: {UNPOSTED, POSTED_NEEDS_REVERT} × {FIRST_DAY, LAST_DAY,
        // RANDOM_MID}. RANDOM_MID is the "typical" case, kept MANDATORY per source-status (same
        // 2-MANDATORY footprint the old 2-variant matrix had); the boundary-specific FIRST_DAY/
        // LAST_DAY variants are REGRESSION_PHASE1 so the mandatory suite's runtime stays roughly
        // unchanged — easy to retune by moving entries between groups.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_FIRSTDAY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.FIRST_DAY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_LASTDAY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.LAST_DAY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_RANDOMMID")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.RANDOM_MID)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_POSTED_REVERT_FIRSTDAY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT)
                .dateVariant(VoucherEditDateVariant.FIRST_DAY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_POSTED_REVERT_LASTDAY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT)
                .dateVariant(VoucherEditDateVariant.LAST_DAY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_POSTED_REVERT_RANDOMMID")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT)
                .dateVariant(VoucherEditDateVariant.RANDOM_MID)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // ── Boundary header scenarios: each exercises a real, distinct app date-handling rule —
        // worth MANDATORY-tier coverage. All claim an UNPOSTED source voucher (simplest source
        // status). CROSS_YEAR_NEGATIVE is genuinely negative (app rejects) — assertion doesn't
        // depend on post/unpost state.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_CROSS_YEAR_NEGATIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.CROSS_YEAR_INVALID)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // FUTURE_DATE's own expected outcome depends on the claimed candidate's Cash/Bank ledger
        // type — the app accepts a future-dated document for a Bank-type ledger, but rejects one
        // for a Cash-type ledger ("Post dated entries are not allowed for cash transactions").
        // A single unconstrained FUTURE_DATE variant left this to pure luck of which candidate
        // got claimed (confirmed live: FIN-GV-EDIT-HEADER-04 failed exactly this way, claiming a
        // Cash-type voucher and hitting the app's own rejection) — split into two deterministic
        // variants, each constrained via requiredCashBankType so claimOne only ever claims the
        // matching ledger type. See "Round 22" of the GV plan.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_BANK")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.FUTURE_DATE)
                .requiredCashBankType(CashBankType.BANK)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE_CASH_NEGATIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.FUTURE_DATE)
                .requiredCashBankType(CashBankType.CASH)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        // Negative: claims a Posted candidate whose own accounting year/financial month is
        // already closed. sourceStatus=POSTED_NEEDS_REVERT so claimOne's "P"-status branch is
        // used (never DataAvailabilityGuard/backfill-wrapped for that status — see
        // claimCandidate), and requireClosedPeriod=true inverts claimOne's usual period-open
        // guard. Unlike CROSS_YEAR_NEGATIVE above, this never reaches Save at all — the live app
        // already refuses to even Unpost such a candidate (confirmed via
        // FIN-GV-REVERT-05..09's live rejection text), so EditGeneralVoucher's Step 4 "Unpost if
        // Posted" prep step IS this scenario's assertion point (see expectedRevertOutcome on the
        // resulting MergedFinanceGeneralVoucherData row, resolved directly in resolveOne — no
        // detail rows/new date are built for this variant at all).
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_POSTED_MONTH_CLOSED_NEGATIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT)
                .requireClosedPeriod(true)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // ── Field-isolated header scenarios (plan §4D): each holds two of the three header
        // fields at their existing BEFORE value and changes exactly one, so the report row
        // unambiguously demonstrates that single field's own effect instead of always changing
        // Reference AND Remarks AND Date together the way every scenario above does. All three
        // are REGRESSION_PHASE1 tier, all UNPOSTED source (simplest source status, consistent
        // with the boundary scenarios above) — headerFieldScope is the only new dimension.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_REFERENCE_ONLY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .headerFieldScope(GeneralVoucherEditVariant.HeaderFieldScope.REFERENCE_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_REMARKS_ONLY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .headerFieldScope(GeneralVoucherEditVariant.HeaderFieldScope.REMARKS_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_DATE_ONLY")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.RANDOM_MID)
                .headerFieldScope(GeneralVoucherEditVariant.HeaderFieldScope.DATE_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_TYPE1_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE1_NONE)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_TYPE2_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE2_SITE_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_TYPE3_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE3_SITE_AND_SUBLEDGER)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_REVERSAL_TOGGLE_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE1_NONE)
                .reversalToggle(true)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_POSTED_REVERT")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT)
                .glLineType(GlLineType.TYPE1_NONE)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        // Regression guard for the finpost/finvchdet narration-change ambiguity fix — see the
        // "Fix finpost/finvchdet narration-change detection" plan. requireSharedGlcodeSibling
        // makes claimOne() only claim a candidate that genuinely has two finvchdet rows sharing a
        // glcode with different slcode (confirmed live in idealdb — vchcode 597292/562638 etc.
        // have this shape), and buildEditedDetailLine() specifically edits one of those two rows'
        // Ledger Narration — so the snapshot-diff engine's per-line finpost/finvchdet nartext
        // checks are asserted against the exact shape that previously collided. No glLineType
        // constraint: live data shows this shape only on GL accounts with slapp='Y'/costapp='N'
        // — the unmodeled 4th GlLineType flag combination (GlLineType.fromFlags() throws for it,
        // so resolvedGlType stays null for these rows; see resolveExpenseIncomeRows), not any of
        // TYPE1_NONE/TYPE2_SITE_ONLY/TYPE3_SITE_AND_SUBLEDGER — so matching by glLineType would
        // never find this shape.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_NARRATION_SHARED_GLCODE_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .narrationEdit(true)
                .requireSharedGlcodeSibling(true)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // ── New detail-line edit shapes (plan §4B): GL-type transition, add-a-new-line, and
        // site-split-only (Type2/Type3) — the 3 shapes that survived the plan's "remove-and-
        // re-add a line" descope. All REGRESSION_PHASE1, matching every existing non-Type1
        // detail-line variant's tier.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_GLTYPE_TRANSITION_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE1_NONE)
                .detailLineEditShape(DetailLineEditTarget.EditShape.GL_TYPE_TRANSITION)
                .targetAccountClass(GlAccountType.ASSET)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_ADD_NEW_LINE_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                // glLineType here describes the NEW line's own shape, not an existing row to find —
                // buildAddNewLine only ever adds a TYPE1_NONE line (see its Javadoc).
                .glLineType(GlLineType.TYPE1_NONE)
                .detailLineEditShape(DetailLineEditTarget.EditShape.ADD_NEW_LINE)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_SITE_SPLIT_ONLY_TYPE2_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE2_SITE_ONLY)
                .detailLineEditShape(DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_DETAIL_SITE_SPLIT_ONLY_TYPE3_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .glLineType(GlLineType.TYPE3_SITE_AND_SUBLEDGER)
                .detailLineEditShape(DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        // #24 — Payment-Info edit: changes the claimed voucher's existing Cheque instrument
        // number to a fresh known/same-bank one. requiredCashBankType=BANK reuses claimOne's
        // existing Cash/Bank-type filter machinery (previously only used by the FUTURE_DATE
        // variants) — a Cheque/NEFT payment concept is only meaningful against a Bank-type
        // ledger. Promoted to MANDATORY (was REGRESSION_PHASE1) once the GV Cheque Dr/Cr
        // bifurcation fix landed — by explicit request, ahead of this scenario's own originally-
        // planned "prove stable in regression first" staging, so treat its first few live runs
        // as still-unverified rather than assumed-green.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_CHEQUE_INSTRUMENT_CHANGE_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // #25/#26 — Payment mode change (Cheque <-> NEFT/RTGS): swaps the payment MODE itself,
        // not just the instrument number within Cheque mode. Same MANDATORY promotion and
        // caveat as #24 above — additionally doubly unverified here since a mode-switch's effect
        // on the Instrument No./Instrument Date fields has never been exercised at all.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_MODE_CHEQUE_TO_NEFT_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_MODE_NEFT_TO_CHEQUE_UNPOSTED")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoEditTarget.PaymentModeShape.NEFT_TO_CHEQUE)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        // #27-30 — PaymentInfoDateScenario: Instrument Date vs. entry-date validation. Each
        // variant sets BOTH paymentInfoEditShape (from dateScenario.shape(), so every existing
        // variant.getPaymentInfoEditShape() call site keeps working unchanged) and dateScenario
        // itself (which additionally pins the claimed candidate's counter-leg to Dr/Cr and
        // overrides the new Instrument Date — see PaymentInfoDateScenario's own class Javadoc for
        // the full Dr/Cr x Cheque/NEFT x future/current combination analysis and why only these 4
        // of the 8 theoretical combinations are meaningfully testable via the live UI).
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_MODE_NEFT_FUTURE_INSTRUMENT_DATE_NEGATIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoDateScenario.NEFT_FUTURE_DATE_CR_REJECTED.shape())
                .dateScenario(PaymentInfoDateScenario.NEFT_FUTURE_DATE_CR_REJECTED)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_MODE_NEFT_CURRENT_INSTRUMENT_DATE_POSITIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoDateScenario.NEFT_CURRENT_DATE_CR_ACCEPTED.shape())
                .dateScenario(PaymentInfoDateScenario.NEFT_CURRENT_DATE_CR_ACCEPTED)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_CHEQUE_FUTURE_INSTRUMENT_DATE_CR_POSITIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoDateScenario.CHEQUE_FUTURE_DATE_CR_ACCEPTED.shape())
                .dateScenario(PaymentInfoDateScenario.CHEQUE_FUTURE_DATE_CR_ACCEPTED)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_PAYMENT_CHEQUE_FUTURE_INSTRUMENT_DATE_DR_POSITIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .requiredCashBankType(CashBankType.BANK)
                .paymentInfoEditShape(PaymentInfoDateScenario.CHEQUE_FUTURE_DATE_DR_ACCEPTED.shape())
                .dateScenario(PaymentInfoDateScenario.CHEQUE_FUTURE_DATE_DR_ACCEPTED)
                .executionGroup(GeneralVoucherEditExecutionGroup.REGRESSION_PHASE1)
                .build());

        // Final pass: alternate the two live entry paths across every scenario above (plan
        // §9.5), via the shared, domain-agnostic VoucherEditEntryMethod helper rather than a
        // literal per-variant — see that enum's Javadoc for the even/odd rule.
        for (int i = 0; i < out.size(); i++) {
            out.set(i, out.get(i).toBuilder()
                    .entryMethod(VoucherEditEntryMethod.forIndex(i))
                    .build());
        }

        return out;
    }
}
