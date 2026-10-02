package com.gsl.validation.db.finance;

import com.gsl.testdata.Finance.Setup.AccountClass.AccountClassLifecycleRow;

/**
 * Assembles an {@link AccountClassDbContext} from a row's static Excel fields plus the
 * already-resolved (post-{@code uniqueify}) names the test actually submitted to the UI this
 * run — mirrors {@code VoucherDbContextBuilder}'s role of packaging pre-computed facts for the
 * "Database Validation" step, just without any thread-local pre/post-state capture (admcls has
 * no equivalent of the finpost snapshot).
 */
public final class AccountClassDbContextBuilder {

    private AccountClassDbContextBuilder() {}

    /** {@link #build(AccountClassLifecycleRow, String, String, String)} with no edit/duplicate-variant facts. */
    public static AccountClassDbContext build(AccountClassLifecycleRow row, String resolvedClassName) {
        return build(row, resolvedClassName, null, null);
    }

    /**
     * Assembles the context from {@code row}'s static Excel fields plus the resolved names this
     * run actually submitted.
     *
     * @param resolvedClassName    the Class Name string submitted to the Add popup this run
     * @param resolvedNewClassName the renamed Class Name submitted on Edit, or {@code null} when
     *                             {@code row} has no edit or the edit didn't change the name
     * @param duplicateVariantName the exact/case-varied name attempted for the duplicate-variant
     *                             self-check, or {@code null} when {@code row} has none
     */
    public static AccountClassDbContext build(AccountClassLifecycleRow row, String resolvedClassName,
                                               String resolvedNewClassName, String duplicateVariantName) {
        boolean edited = "Y".equalsIgnoreCase(row.getEditAfterCreate());
        String newType = row.getNewClassTypeIfEdited();

        return AccountClassDbContext.builder()
                .resolvedClassName(resolvedClassName)
                .expectedClassType(row.getClassType())
                .expectedOutcome(row.getExpectedOutcome())
                .edited(edited)
                .newResolvedClassName(edited ? resolvedNewClassName : null)
                .newExpectedClassType(edited && newType != null && !newType.isBlank() ? newType : null)
                .duplicateVariant(row.getDuplicateVariantAfterCreate())
                .duplicateVariantName(duplicateVariantName)
                .build();
    }
}
