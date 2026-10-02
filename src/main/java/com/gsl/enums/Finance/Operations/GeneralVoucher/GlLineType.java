package com.gsl.enums.Finance.Operations.GeneralVoucher;

/**
 * The 3 GL behavior types a General Voucher "Expense/Income Detail" grid row
 * can be, driven by two flags on the GL master ({@code fingl.slapp} = Sub
 * Ledger Applicable, {@code fingl.costapp} = Site Applicable):
 *
 * <table>
 *     <caption>GL line type matrix</caption>
 *     <tr><th>Type</th><th>Site Applicable</th><th>Sub Ledger Applicable</th></tr>
 *     <tr><td>{@link #TYPE1_NONE}</td><td>No</td><td>No</td></tr>
 *     <tr><td>{@link #TYPE2_SITE_ONLY}</td><td>Yes</td><td>No</td></tr>
 *     <tr><td>{@link #TYPE3_SITE_AND_SUBLEDGER}</td><td>Yes</td><td>Yes</td></tr>
 * </table>
 *
 * <p>A single voucher mixes all 3 types across its rows, so the row-fill
 * logic in {@code GeneralVoucherExpenseIncomeDetail} branches per row on
 * this type, not per voucher.
 *
 * <p>The DB ({@code fingl.slapp}/{@code fingl.costapp}) is the source of
 * truth for a GL's type — an Excel test-data row's optional
 * {@code Expected GL Type} column is only a soft assertion against the
 * DB-resolved value, never authoritative on its own.
 */
public enum GlLineType {

    TYPE1_NONE,
    TYPE2_SITE_ONLY,
    TYPE3_SITE_AND_SUBLEDGER;

    /**
     * Resolves the GL line type from the GL master's applicability flags.
     *
     * @param siteApplicable      {@code fingl.costapp = 'Y'}
     * @param subLedgerApplicable {@code fingl.slapp = 'Y'}
     */
    public static GlLineType fromFlags(boolean siteApplicable, boolean subLedgerApplicable) {
        if (siteApplicable && subLedgerApplicable) {
            return TYPE3_SITE_AND_SUBLEDGER;
        }
        if (siteApplicable) {
            return TYPE2_SITE_ONLY;
        }
        if (subLedgerApplicable) {
            throw new IllegalArgumentException(
                    "GL has Sub Ledger Applicable='Y' but Site Applicable='N' — not one of the " +
                    "3 supported General Voucher GL types (Site-only-No-SubLedger combination " +
                    "is not modeled; confirmed to exist in live master data, e.g. glcode 2 " +
                    "'Staff Advances' in idealdb, but never exercised by a real General Voucher). " +
                    "Verify the chosen GL is actually a Type1/2/3 GL before using it in test data.");
        }
        return TYPE1_NONE;
    }

    public boolean isSiteApplicable() {
        return this == TYPE2_SITE_ONLY || this == TYPE3_SITE_AND_SUBLEDGER;
    }

    public boolean isSubLedgerApplicable() {
        return this == TYPE3_SITE_AND_SUBLEDGER;
    }

    /**
     * Human-readable label for report display — a business reader has no reason to know what
     * "TYPE2_SITE_ONLY" means. Mirrors the exact phrases
     * {@link com.gsl.reports.GeneralVoucherDescriptionBuilder#build}'s {@code glTypePhrase} switch
     * already uses in the test-case-name sentence, so the two never drift.
     */
    public String label() {
        return switch (this) {
            case TYPE1_NONE -> "No Site/Sub Ledger";
            case TYPE2_SITE_ONLY -> "Site allocation only";
            case TYPE3_SITE_AND_SUBLEDGER -> "Site + Sub Ledger";
        };
    }
}
