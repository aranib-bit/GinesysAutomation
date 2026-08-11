package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates the full General Voucher Expense/Income Detail combination matrix — the GV
 * analogue of {@link SelectDocumentCombinations}.
 *
 * <p><b>Dimensions</b> (see the approved plan's "Combination matrix" section):
 * <ul>
 *   <li>GL Account Type — Expense/Income/Asset/Liability ({@link GlAccountType})</li>
 *   <li>Site/SubLedger shape — driven by {@link #shapesFor}, NOT a uniform
 *       {@link GlLineType#values()} sweep: Expense/Income are restricted to
 *       {@link GlLineType#TYPE2_SITE_ONLY}/{@link GlLineType#TYPE3_SITE_AND_SUBLEDGER} only —
 *       {@link GlLineType#TYPE1_NONE} (Site not applicable) is structurally impossible for
 *       Expense/Income per the confirmed business rule (Expense/Income ledgers must always be
 *       Site applicable; Sub Ledger applicable can still be Yes/No). Asset/Liability keep all 3
 *       shapes, unchanged (Site=No/SubLedger=Yes remains unsupported and excluded, per
 *       {@link GlLineType#fromFlags}'s existing throw).</li>
 *   <li>Amount side — Expense=Debit-only, Income=Credit-only, Asset/Liability=both</li>
 *   <li>IsReversal — No/Yes</li>
 *   <li>Leg+Payment — Bank+Cheque / Bank+NEFT-RTGS / Bank+None / Cash
 *       ({@link GeneralVoucherPaymentVariant})</li>
 * </ul>
 * Expense(2 shapes×1 side) + Income(2×1) + Asset(3×2) + Liability(3×2) = 16 (type, shape, side)
 * tuples × 2 reversal × 4 payment variants = <b>128 total combinations</b> (down from an earlier
 * 144-combination version that incorrectly included Expense/Income at Type1 — removed once the
 * business rule above was confirmed; see the plan's "Matrix conflict" resolution).
 *
 * <p><b>Tier assignment</b> — {@link #resolveExecutionGroup} partitions the 128 combinations into
 * 9 Mandatory + 10 Phase1 + 46 Phase2 + 63 Phase3 = 128, preserving the original tiering
 * philosophy (a small baseline-shape smoke set per type in Mandatory, a single-payment-variant
 * sweep in Phase1, the non-reversal remainder in Phase2, the reversal sweep in Phase3) with
 * Expense/Income's baseline shape moved from the now-impossible Type1 to
 * {@link GlLineType#TYPE2_SITE_ONLY} (the simplest shape still valid for them) — see
 * {@link #isBaselineShape}:
 * <ul>
 *   <li><b>MANDATORY</b> (9): Expense/Income at their baseline shape (Type2 for these two types),
 *       Reversal=No, all 4 Leg+Payment values (8), plus one Reversal=Yes smoke case
 *       (Expense/Type2/Debit/Bank+Cheque).</li>
 *   <li><b>REGRESSION_PHASE1</b> (10): every type's non-baseline shape(s) — Type3 only for
 *       Expense/Income, Type2+Type3 for Asset/Liability — Leg+Payment=Bank+Cheque, Reversal=No.</li>
 *   <li><b>REGRESSION_PHASE2</b> (46): Asset/Liability's first exposure at their baseline shape
 *       (Type1), all 4 Leg+Payment values, Reversal=No (16), plus every type's non-baseline
 *       shape(s) swept across the remaining 3 Leg+Payment values (30).</li>
 *   <li><b>REGRESSION_PHASE3</b> (63): the Reversal=Yes mirror of everything, minus the 1 case
 *       already covered by Mandatory.</li>
 * </ul>
 */
public final class GeneralVoucherCombinations {

    private GeneralVoucherCombinations() {}

    /**
     * Full combination matrix — the single source of truth. Returns the original 128-row
     * Cartesian product (unchanged tiering, see class Javadoc) plus a small, bounded set of
     * additional {@code MULTI_GL}/{@code N_SITES}/{@code MULTI_GL_N_SITES} variants layered on
     * top for the Site-applicable shapes — see {@link #multiCountVariants()}. Mirrors how
     * {@code SelectDocumentCombinations}' Groups 3-8 add SL/Doc-count variants on top of its
     * classification-type dimension without touching Group 0-2's own tiering.
     */
    public static List<GeneralVoucherVariant> allCombinations() {
        List<GeneralVoucherVariant> out = new ArrayList<>();
        for (GlAccountType type : GlAccountType.values()) {
            for (GlLineType shape : shapesFor(type)) {
                for (boolean debitSide : sidesFor(type)) {
                    for (boolean reversal : new boolean[]{false, true}) {
                        for (GeneralVoucherPaymentVariant payment : GeneralVoucherPaymentVariant.values()) {
                            out.add(buildVariant(type, shape, debitSide, reversal, payment, 1, 1));
                        }
                    }
                }
            }
        }
        out.addAll(multiCountVariants());
        return out;
    }

    /**
     * Additional {@code MULTI_GL}/{@code N_SITES}/{@code MULTI_GL_N_SITES} variants — the GV
     * analogue of {@code SelectDocumentCombinations}' Group 4 (SL count × Doc count), which also
     * uses a small, fixed set of static variants rather than crossing every classification type.
     * One representative (Reversal=No, NONE payment, type-appropriate side) per Site-applicable
     * (accountType, glLineType) pair — 8 pairs × 3 count-variants = 24 additional rows, all
     * REGRESSION_PHASE2 (new coverage, doesn't disturb the existing 128's tiering).
     */
    private static List<GeneralVoucherVariant> multiCountVariants() {
        List<GeneralVoucherVariant> out = new ArrayList<>();
        for (GlAccountType type : GlAccountType.values()) {
            for (GlLineType shape : shapesFor(type)) {
                if (!shape.isSiteApplicable()) continue; // TYPE1_NONE has no Site dimension
                boolean debitSide = sidesFor(type)[0];
                out.add(buildMultiCountVariant(type, shape, debitSide, 3, 1)); // MULTI_GL
                out.add(buildMultiCountVariant(type, shape, debitSide, 1, 3)); // N_SITES
                out.add(buildMultiCountVariant(type, shape, debitSide, 3, 3)); // MULTI_GL_N_SITES
            }
        }
        return out;
    }

    /**
     * Builds one multi-count variant, forcing REGRESSION_PHASE2 regardless of what
     * {@link #resolveExecutionGroup} would otherwise assign — Type2 is Expense/Income's baseline
     * shape, so without this override their multi-count variants would incorrectly land in
     * MANDATORY (see {@link #resolveExecutionGroup}'s baseline-shape branch). {@code glCount}/
     * {@code siteCount} are already unique per label (see {@link #labelCore}) — no extra suffix needed.
     */
    private static GeneralVoucherVariant buildMultiCountVariant(GlAccountType type, GlLineType shape,
            boolean debitSide, int glCount, int siteCount) {
        return buildVariant(type, shape, debitSide, false, GeneralVoucherPaymentVariant.NONE, glCount, siteCount)
                .toBuilder()
                .executionGroup(GeneralVoucherExecutionGroup.REGRESSION_PHASE2)
                .build();
    }

    /** Expense=Debit-only, Income=Credit-only, Asset/Liability=both — the user-confirmed amount-side rule. */
    private static boolean[] sidesFor(GlAccountType type) {
        if (type == GlAccountType.EXPENSE) return new boolean[]{true};
        if (type == GlAccountType.INCOME) return new boolean[]{false};
        return new boolean[]{true, false}; // ASSET, LIABILITY
    }

    /**
     * Valid Site/SubLedger shapes per type — Expense/Income exclude {@link GlLineType#TYPE1_NONE}
     * entirely (Site not applicable is structurally impossible for them per the confirmed
     * business rule); Asset/Liability keep all 3 shapes, unchanged.
     */
    private static GlLineType[] shapesFor(GlAccountType type) {
        boolean isExpenseOrIncome = type == GlAccountType.EXPENSE || type == GlAccountType.INCOME;
        return isExpenseOrIncome
                ? new GlLineType[]{GlLineType.TYPE2_SITE_ONLY, GlLineType.TYPE3_SITE_AND_SUBLEDGER}
                : GlLineType.values();
    }

    /**
     * Account-type initial used in the ledger-type label — A(sset)/L(iability)/I(ncome)/E(xpense).
     */
    private static String accountInitial(GlAccountType type) {
        return switch (type) {
            case ASSET -> "A";
            case LIABILITY -> "L";
            case INCOME -> "I";
            case EXPENSE -> "E";
        };
    }

    /**
     * GL-line-type shape label used in the ledger-type name — describes what the row requires
     * (nothing / a Site / a Site + Sub Ledger), replacing the raw {@code TYPE1_NONE}-style enum
     * name in test-case titles/descriptions.
     */
    private static String shapeLabel(GlLineType shape) {
        return switch (shape) {
            case TYPE1_NONE -> "nothing_applicable";
            case TYPE2_SITE_ONLY -> "site_applicable_only";
            case TYPE3_SITE_AND_SUBLEDGER -> "site_and_subledger_applicable";
        };
    }

    /** The "type of ledger" label, e.g. {@code "L_site_applicable_only"} — see class Javadoc. */
    private static String ledgerTypeLabel(GlAccountType type, GlLineType shape) {
        return accountInitial(type) + "_" + shapeLabel(shape);
    }

    /**
     * Builds the shared label core, e.g. {@code "L_site_applicable_only_1GL_1SITE_DR_REV_N_NEFT_RTGS"}
     * — used verbatim as both {@code variantId} (GV analogue of AR's compact token id) and
     * {@code testCaseName} (shown as-is in the Description/TestCase ID/TestCase Name report fields,
     * since GV — unlike AR/AP — has no separate rich-English-vs-compact-token split at this layer;
     * the English sentence is built separately by {@code GeneralVoucherDescriptionBuilder}).
     */
    private static String labelCore(GlAccountType type, GlLineType shape, boolean debitSide,
            boolean reversal, GeneralVoucherPaymentVariant payment, int glCount, int siteCount) {
        return ledgerTypeLabel(type, shape)
                + "_" + glCount + "GL_" + siteCount + "SITE"
                + "_" + (debitSide ? "DR" : "CR")
                + "_REV_" + (reversal ? "Y" : "N")
                + "_" + payment.name();
    }

    private static GeneralVoucherVariant buildVariant(GlAccountType type, GlLineType shape,
            boolean debitSide, boolean reversal, GeneralVoucherPaymentVariant payment,
            int glCount, int siteCount) {
        GeneralVoucherExecutionGroup group = resolveExecutionGroup(type, shape, debitSide, reversal, payment);
        String label = labelCore(type, shape, debitSide, reversal, payment, glCount, siteCount);
        return GeneralVoucherVariant.builder()
                .variantId(label)
                .testCaseName(label)
                .accountType(type)
                .glLineType(shape)
                .debitSide(debitSide)
                .reversal(reversal)
                .paymentVariant(payment)
                .executionGroup(group)
                .glCount(glCount)
                .siteCount(siteCount)
                .build();
    }

    /**
     * Each type's baseline shape — the smallest-footprint shape still valid for it, used to pick
     * the Mandatory-tier smoke set. Asset/Liability's baseline stays {@link GlLineType#TYPE1_NONE}
     * (unchanged); Expense/Income's baseline moves to {@link GlLineType#TYPE2_SITE_ONLY} since
     * {@code TYPE1_NONE} no longer exists in their {@link #shapesFor} at all.
     */
    private static boolean isBaselineShape(GlAccountType type, GlLineType shape) {
        boolean isExpenseOrIncome = type == GlAccountType.EXPENSE || type == GlAccountType.INCOME;
        return isExpenseOrIncome ? shape == GlLineType.TYPE2_SITE_ONLY : shape == GlLineType.TYPE1_NONE;
    }

    /**
     * Encodes the Mandatory/Phase1/Phase2/Phase3 assignment — same tiering philosophy as the
     * original 144-combination version (a small baseline-shape smoke set per type in Mandatory, a
     * single-payment-variant sweep in Phase1, the non-reversal remainder in Phase2, the reversal
     * sweep in Phase3), re-derived after {@link #shapesFor} dropped Expense/Income's now-impossible
     * Type1 shape (see this class's own Javadoc for the "Matrix conflict" resolution). Reduces to:
     * <pre>
     *   Reversal=No, baseline shape,     type=E/I → MANDATORY     (2 tuples × 4 payment = 8)
     *   Reversal=No, baseline shape,     type=A/L → PHASE2        (4 tuples × 4 payment = 16)
     *   Reversal=No, non-baseline shape, payment=BANK+CHEQUE → PHASE1  (10 tuples × 1 payment = 10)
     *   Reversal=No, non-baseline shape, payment!=BANK+CHEQUE → PHASE2 (10 tuples × 3 payment = 30)
     *   Reversal=Yes, Expense/baseline/Debit/Bank+Cheque → MANDATORY   (the 1 smoke case)
     *   Reversal=Yes, everything else → PHASE3                        ((8×4 - 1) + (8×4) = 63)
     * </pre>
     * ("non-baseline shape" tuple count is 10: Expense/Income contribute 1 each — only Type3
     * remains after Type2 becomes their baseline — and Asset/Liability contribute 4 each — Type2
     * and Type3, ×2 sides) which totals 8+16+10+30+1+63 = 9(Mandatory)+10(Phase1)+46(Phase2)+63(Phase3) = 128.
     */
    private static GeneralVoucherExecutionGroup resolveExecutionGroup(GlAccountType type, GlLineType shape,
            boolean debitSide, boolean reversal, GeneralVoucherPaymentVariant payment) {

        boolean isExpenseOrIncome = type == GlAccountType.EXPENSE || type == GlAccountType.INCOME;
        boolean isBaseline = isBaselineShape(type, shape);

        if (!reversal) {
            if (isBaseline) {
                return isExpenseOrIncome
                        ? GeneralVoucherExecutionGroup.MANDATORY
                        : GeneralVoucherExecutionGroup.REGRESSION_PHASE2;
            }
            // non-baseline shape: Type3 only for Expense/Income, Type2/Type3 for Asset/Liability
            return payment == GeneralVoucherPaymentVariant.CHEQUE
                    ? GeneralVoucherExecutionGroup.REGRESSION_PHASE1
                    : GeneralVoucherExecutionGroup.REGRESSION_PHASE2;
        }

        // reversal == true
        boolean isMandatorySmokeCase = isBaseline && type == GlAccountType.EXPENSE && debitSide
                && payment == GeneralVoucherPaymentVariant.CHEQUE;
        return isMandatorySmokeCase
                ? GeneralVoucherExecutionGroup.MANDATORY
                : GeneralVoucherExecutionGroup.REGRESSION_PHASE3;
    }
}
