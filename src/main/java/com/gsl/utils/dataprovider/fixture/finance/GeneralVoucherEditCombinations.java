package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the 21-scenario General Voucher Edit matrix (originally a fixed 7 — plan §7's
 * table — expanded with accounting-year-aware header date variants, see the "GV Edit:
 * accounting-year-aware dates..." plan; #17 (formerly #14) added to verify the "finpost narration
 * change detection" fix — see the "Fix finpost/finvchdet narration-change detection" plan; #9-11
 * added for field-isolated header edits — see plan §4D; #18-20 added for the new detail-line edit
 * shapes — see plan §4B):
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
 *     <tr><td>8</td><td>MANDATORY</td><td>Positive: header-only edit, date is in the future (same accounting year) — app confirmed to accept future-dated documents</td></tr>
 *     <tr><td>9</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=REFERENCE_ONLY — only Reference changes, Remarks/Date held unchanged</td></tr>
 *     <tr><td>10</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=REMARKS_ONLY — only Remarks changes, Reference/Date held unchanged</td></tr>
 *     <tr><td>11</td><td>REGRESSION_PHASE1</td><td>Header-only edit, source Unposted, headerFieldScope=DATE_ONLY — only Date changes, Reference/Remarks held unchanged</td></tr>
 *     <tr><td>12</td><td>MANDATORY</td><td>Detail-line edit, Type1, source Unposted</td></tr>
 *     <tr><td>13</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, Type2, source Unposted</td></tr>
 *     <tr><td>14</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, Type3, source Unposted</td></tr>
 *     <tr><td>15</td><td>REGRESSION_PHASE1</td><td>Detail-line edit toggling Is Reversal, source Unposted</td></tr>
 *     <tr><td>16</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, source Posted (revert-backfill)</td></tr>
 *     <tr><td>17</td><td>MANDATORY</td><td>Detail-line edit, source Unposted, editing Ledger Narration on a
 *         line whose glcode is shared with a sibling line of a different slcode — regression guard for the
 *         finpost/finvchdet narration-change ambiguity fix (confirmed live: real rows share a glcode with
 *         different slcode and narration; live data shows this only on {@code fingl.slapp='Y'}/
 *         {@code costapp='N'} GL accounts — the unmodeled 4th {@code GlLineType} combination, hence no
 *         {@code glLineType} constraint on this variant)</td></tr>
 *     <tr><td>18</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, GL-type transition (Type1 line moves to
 *         a different, unused Asset-class GL), source Unposted</td></tr>
 *     <tr><td>19</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, add a brand-new Type1 line, source Unposted</td></tr>
 *     <tr><td>20</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, site-split-only (Type2), source Unposted —
 *         same total amount, sitewise allocation redistributed</td></tr>
 *     <tr><td>21</td><td>REGRESSION_PHASE1</td><td>Detail-line edit, site-split-only (Type3), source Unposted —
 *         same total amount, sitewise allocation redistributed</td></tr>
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
        // worth MANDATORY-tier coverage. Both claim an UNPOSTED source voucher (simplest source
        // status). CROSS_YEAR_NEGATIVE is genuinely negative (app rejects); FUTURE_DATE below is
        // positive (app confirmed to accept a future-dated document within the same accounting
        // year) — neither assertion depends on post/unpost state.
        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_CROSS_YEAR_NEGATIVE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.CROSS_YEAR_INVALID)
                .executionGroup(GeneralVoucherEditExecutionGroup.MANDATORY)
                .build());

        out.add(GeneralVoucherEditVariant.builder()
                .variantId("GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE")
                .scenarioType(GeneralVoucherEditVariant.ScenarioType.HEADER_ONLY)
                .sourceStatus(GeneralVoucherEditVariant.SourceStatus.UNPOSTED)
                .dateVariant(VoucherEditDateVariant.FUTURE_DATE)
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
