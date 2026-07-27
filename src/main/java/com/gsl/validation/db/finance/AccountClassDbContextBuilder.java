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

    public static AccountClassDbContext build(AccountClassLifecycleRow row, String resolvedClassName) {
        return build(row, resolvedClassName, null, null);
    }

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
