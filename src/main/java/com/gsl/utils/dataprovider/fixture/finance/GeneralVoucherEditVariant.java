package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.pages.Finance.Operations.GeneralVoucherAdd;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import lombok.Builder;
import lombok.Getter;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Describes one General Voucher Edit scenario — the Edit analogue of {@link GeneralVoucherVariant}
 * — plan §7. Instances are created by {@link GeneralVoucherEditCombinations#allCombinations()}.
 */
@Builder(toBuilder = true)
@Getter
public class GeneralVoucherEditVariant {

    /** Unique short identifier used as the test-case label. */
    String variantId;

    /** Whether this scenario edits only the document header, or one existing detail line. */
    ScenarioType scenarioType;

    /** Whether the claimed source voucher is already Unposted, or Posted (needs the
     *  revert-backfill prerequisite — see {@code GeneralVoucherEditRevertBackfillStrategy}). */
    SourceStatus sourceStatus;

    /** Which {@link GlLineType} shape the edited detail line must have — {@code null} for
     *  {@link ScenarioType#HEADER_ONLY} (no detail line is touched). */
    GlLineType glLineType;

    /** {@code true} when this scenario also toggles the edited line's Is Reversal flag —
     *  only meaningful for {@link ScenarioType#DETAIL_LINE}. */
    boolean reversalToggle;

    /** {@code true} when this scenario also edits the edited line's own Ledger Narration (as
     *  opposed to only amount/reversal) — only meaningful for {@link ScenarioType#DETAIL_LINE}.
     *  Exercises {@code VoucherEditChangeSets.detailLineEdit}'s per-line {@code nartext}
     *  assertions on {@code finvchdet} and the matching {@code finpost} row. */
    boolean narrationEdit;

    /** {@code true} when the claimed candidate voucher must have another {@code finvchdet} line
     *  sharing the edited line's {@code glcode} with a different {@code slcode} — i.e. the exact
     *  ambiguity shape {@code VoucherEditChangeSets.findFinpostRowByGlcode} previously resolved
     *  incorrectly (confirmed live: two real rows, same {@code glcode}, different {@code slcode}
     *  and narration). Only meaningful for {@link ScenarioType#DETAIL_LINE} with a sub-ledger-
     *  applicable {@link #glLineType}. */
    boolean requireSharedGlcodeSibling;

    /**
     * {@code true} only for the one negative scenario that deliberately claims a Posted candidate
     * whose own accounting year or financial month is already CLOSED — the exact inverse of the
     * period-open guard {@link com.gsl.utils.dataprovider.source.finance.DbGeneralVoucherEditDataSource#claimOne}
     * otherwise applies to every other variant. Such a candidate can't even be Unposted (the
     * live app rejects Revert itself with "Finance month is already closed. Cannot be
     * reverted." — see {@code GeneralVoucherRevertOutcome#CANNOT_REVERT_MONTH_CLOSED}), so
     * {@code EditGeneralVoucher}'s own "Unpost if Posted" prep step is this scenario's actual,
     * confirmed assertion point — it never reaches Save at all. {@code false} (default) for every
     * other variant, which all require an OPEN period exactly as before.
     */
    @Builder.Default
    boolean requireClosedPeriod = false;

    /** Which new document date to assign — {@code null} for {@link ScenarioType#DETAIL_LINE}
     *  scenarios (date isn't part of that scenario) and for any variant built before this field
     *  existed; {@code DbGeneralVoucherEditDataSource} treats a {@code null} value the same as
     *  {@link VoucherEditDateVariant#RANDOM_MID} (see {@code AccountingPeriodResolver}). Domain-
     *  agnostic type (not nested here), same rationale as {@link #entryMethod}'s Javadoc. */
    VoucherEditDateVariant dateVariant;

    /** Execution bucket this variant belongs to — see {@link GeneralVoucherEditExecutionGroup}. */
    GeneralVoucherEditExecutionGroup executionGroup;

    /** Which live UI path opens the voucher for edit — Actions→Edit or double-click the row
     *  (plan §9.5). Set by {@link GeneralVoucherEditCombinations#allCombinations()} — alternated
     *  across the 7 scenarios via {@link VoucherEditEntryMethod#forIndex(int)}, not hand-written
     *  per variant — and read by {@code EditGeneralVoucher} to branch its "Open Voucher for
     *  Edit" step. Domain-agnostic type (not nested here) so AR/AP's own future Edit variants
     *  reuse the same enum — see {@link VoucherEditEntryMethod}'s Javadoc. */
    VoucherEditEntryMethod entryMethod;

    /** Which header field(s) this {@link ScenarioType#HEADER_ONLY} scenario actually changes —
     *  {@code ALL} (the pre-existing behavior: Reference AND Remarks AND Date all change together)
     *  by default, so every variant built before this field existed needs no change. A single-
     *  field scope (e.g. {@code REFERENCE_ONLY}) holds the other two header fields at the header's
     *  existing {@code getVchref()}/{@code getVchdt()}/{@code getNartext()} value — same "hold
     *  unchanged" idiom {@code DbGeneralVoucherEditDataSource}'s {@code DETAIL_LINE} branch already
     *  uses for the fields a given scenario doesn't touch. Only meaningful for
     *  {@link ScenarioType#HEADER_ONLY}; {@code null}/unset for {@link ScenarioType#DETAIL_LINE}. */
    @Builder.Default
    HeaderFieldScope headerFieldScope = HeaderFieldScope.ALL;

    /** Which of the 4 detail-line edit shapes this scenario is (plan §4B) — {@code IN_PLACE}
     *  (amount/reversal/narration only, same GL/Sub Ledger — the original, still most common
     *  shape) by default, so every variant built before this field existed needs no change. Only
     *  meaningful for {@link ScenarioType#DETAIL_LINE}; {@code IN_PLACE}/unset for
     *  {@link ScenarioType#HEADER_ONLY}. "Remove-and-re-add a line" is deliberately not modeled —
     *  see {@link DetailLineEditTarget.EditShape}'s Javadoc. */
    @Builder.Default
    DetailLineEditTarget.EditShape detailLineEditShape = DetailLineEditTarget.EditShape.IN_PLACE;

    /** Meaningful only for {@link DetailLineEditTarget.EditShape#GL_TYPE_TRANSITION} — the target
     *  GL's account class (Asset/Liability/Income/Expense) to transition the edited line into.
     *  {@code null} for every other shape. */
    GlAccountType targetAccountClass;

    /**
     * When non-null, constrains {@code claimOne} to a candidate whose OWN Cash/Bank ledger
     * (finvchmain.glcode -&gt; fingl.srctype) matches this type — currently only meaningful for
     * the {@code FUTURE_DATE}-dated-variant scenarios, where Cash- vs. Bank-type changes the
     * app's own expected outcome (see {@code NegativeValidation.GV_CASH_POST_DATED_NOT_ALLOWED}
     * — Round 22's fix for FIN-GV-EDIT-HEADER-04, splitting the old single ambiguous
     * {@code GV_EDIT_HEADER_UNPOSTED_FUTURE_DATE} variant into a deterministic Bank-type
     * positive scenario and a deterministic Cash-type negative one). {@code null} (default) for
     * every other variant — no constraint, exactly today's pre-Round-22 behavior.
     */
    CashBankType requiredCashBankType;

    /** Which of the three Payment-Info edit shapes this scenario is (see {@link
     *  PaymentInfoEditTarget.PaymentModeShape}) — {@code SAME_MODE_NEW_INSTRUMENT} (the original
     *  shape) by default, so every variant built before this field existed needs no change. Only
     *  meaningful for {@link ScenarioType#PAYMENT_INFO}; unset/default for every other scenario
     *  type. Mirrors {@link #detailLineEditShape}'s own Javadoc convention exactly. */
    @Builder.Default
    PaymentInfoEditTarget.PaymentModeShape paymentInfoEditShape = PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT;

    /** {@code null} (default) for every ordinary Payment-Info variant — today's existing behavior,
     *  completely unchanged. Set only for the 4 dedicated "instrument date vs. entry date"
     *  regression scenarios ({@link PaymentInfoDateScenario}), which additionally pin the claimed
     *  candidate's counter-leg to Dr or Cr (see that enum's own class Javadoc for why) and override
     *  the new Instrument Date to be genuinely future-dated. {@code paymentInfoEditShape} above is
     *  still set explicitly alongside this (mirroring {@code dateScenario.shape()}) rather than
     *  derived from it, so every existing {@code variant.getPaymentInfoEditShape()} call site keeps
     *  working unchanged for both old and new variants alike. */
    PaymentInfoDateScenario dateScenario;

    /**
     * Plan §4K: each constant now also carries the {@code Supplier<BiPredicate<Data, Page>>}
     * behavior that applies this scenario's change to the open Edit form — mirroring
     * {@code ArVoucherScenarioType}'s templateLoader+behaviorSupplier shape (data-building stays
     * bespoke here, in {@code DbGeneralVoucherEditDataSource}/{@code ExcelGeneralVoucherEditDataSource},
     * so only the behavior half is owned here — see {@code GeneralVoucherEditPredicateFactory}'s
     * Javadoc for why the snapshot-capture tail is deliberately NOT folded into these behaviors
     * too). {@link #execute} replaces the {@code if (detailLineEdit) {...} else {...}} branch
     * that used to live directly in {@code EditGeneralVoucher.runEditFlow}.
     */
    public enum ScenarioType {
        HEADER_ONLY(GeneralVoucherEditPredicateFactory.HEADER_EDIT_BEHAVIOR),
        DETAIL_LINE(GeneralVoucherEditPredicateFactory.DETAIL_LINE_EDIT_BEHAVIOR),
        /** Edits the voucher's existing Cash/Bank Cheque instrument number — see
         *  {@code GeneralVoucherEditPredicateFactory#PAYMENT_INFO_EDIT_BEHAVIOR} and {@code
         *  PaymentInfoEditTarget}. Requires {@link #requiredCashBankType} = {@code BANK} and a
         *  claimed candidate whose first {@code finvchdet} row is already Cheque-mode. */
        PAYMENT_INFO(GeneralVoucherEditPredicateFactory.PAYMENT_INFO_EDIT_BEHAVIOR);

        private final Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>> behaviorSupplier;

        ScenarioType(Supplier<BiPredicate<MergedFinanceGeneralVoucherData, GeneralVoucherAdd>> behaviorSupplier) {
            this.behaviorSupplier = behaviorSupplier;
        }

        /** Dispatches to this scenario type's "Apply Changes" behavior — same shape as
         *  {@code ArVoucherScenarioType#execute}. */
        public boolean execute(MergedFinanceGeneralVoucherData data, GeneralVoucherAdd page) {
            return behaviorSupplier.get().test(data, page);
        }
    }

    public enum SourceStatus { UNPOSTED, POSTED_NEEDS_REVERT }

    /** Which header field(s) a {@link ScenarioType#HEADER_ONLY} variant changes — plan §4D. */
    public enum HeaderFieldScope { ALL, REFERENCE_ONLY, REMARKS_ONLY, DATE_ONLY }
}
