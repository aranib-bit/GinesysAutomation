package com.gsl.utils.dataprovider.fixture.finance;

/**
 * Classifies each General Voucher Expense/Income Detail combination into a named
 * execution bucket — mirrors {@link ApVoucherExecutionGroup}.
 *
 * <p>Pass {@code -Dgv.group=MANDATORY} (or REGRESSION_PHASE1/2/3) to Maven Surefire, or
 * set the {@code gv.group} TestNG XML {@code <parameter>}, to restrict the DataSupplier
 * to that group only. Omit to run all groups.
 *
 * <p>The combination matrix crosses GL Account Type (Expense/Income/Asset/Liability) ×
 * Site/SubLedger shape × Amount side × IsReversal × Leg+Payment
 * (Bank+Cheque/Bank+NEFT-RTGS/Bank+None/Cash) = 128 total combinations. Shape is NOT a
 * uniform 3-value sweep for every type — Expense/Income are restricted to 2 shapes
 * (Site=Yes/SubLedger=No and Site=Yes/SubLedger=Yes) since Site=No is structurally
 * impossible for them (a confirmed business rule: Expense/Income ledgers must always be
 * Site applicable); Asset/Liability keep all 3 shapes. See {@link GeneralVoucherCombinations}
 * for the full enumeration and this tier's exact membership.
 *
 * <h3>Bucket assignments (128 total: 9 + 10 + 46 + 63)</h3>
 * <ul>
 *   <li><b>MANDATORY</b> (9): Expense/Income at their baseline shape (Site Yes/SubLedger No),
 *       Reversal=No, across all 4 Leg+Payment values, plus one Reversal=Yes smoke case.
 *       Asset/Liability are deliberately NOT in Mandatory — moved to Regression.</li>
 *   <li><b>REGRESSION_PHASE1</b> (10): every type's non-baseline shape(s) — Site Yes/SubLedger
 *       Yes only for Expense/Income, plus Site No for Asset/Liability — Leg+Payment fixed to
 *       Bank+Cheque, Reversal=No.</li>
 *   <li><b>REGRESSION_PHASE2</b> (46): the Phase1 tuples swept across the remaining 3
 *       Leg+Payment values (30), plus Asset/Liability's first exposure at their baseline shape
 *       (Site No/SubLedger No) across all 4 Leg+Payment values (16).</li>
 *   <li><b>REGRESSION_PHASE3</b> (63): the Reversal=Yes mirror of everything above, minus
 *       the 1 Reversal=Yes case already covered by Mandatory.</li>
 * </ul>
 */
public enum GeneralVoucherExecutionGroup {
    MANDATORY,
    REGRESSION_PHASE1,
    REGRESSION_PHASE2,
    REGRESSION_PHASE3
}
