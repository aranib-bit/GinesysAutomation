package com.gsl.reports;

import com.gsl.testdata.Finance.Setup.AccountClass.AccountClassLifecycleRow;
import com.gsl.utils.Log;

/**
 * Logs the test data for one Account Class scenario at the very start of the invocation, before
 * Step 1 (Login) runs — same convention as {@code ApVoucherExtentDescriptionLogger.log(data)},
 * so the Extent report shows exactly what this row is about to do. There is no
 * {@code DbGatheringExtentLogger} counterpart here: that class exists specifically for AP
 * Voucher's Excel-vs-DB dual data sourcing, and Account Class is Excel-only.
 */
public final class AccountClassExtentDescriptionLogger {

    private AccountClassExtentDescriptionLogger() {}

    /** Logs {@code row}'s fields as a labeled Extent table and mirrors a one-line summary to the log file. */
    public static void log(AccountClassLifecycleRow row, String resolvedClassName) {
        String[][] table = {
                {"Field", "Value"},
                {"TestCaseID", nullSafe(row.getTestCaseId())},
                {"ClassName (Excel)", nullSafe(row.getClassName())},
                {"ClassName (resolved)", nullSafe(resolvedClassName)},
                {"ClassType", nullSafe(row.getClassType())},
                {"NamingCategory", nullSafe(row.getNamingCategory())},
                {"ExpectedOutcome", nullSafe(row.getExpectedOutcome())},
                {"EditAfterCreate", nullSafe(row.getEditAfterCreate())},
                {"NewClassNameIfEdited", nullSafe(row.getNewClassNameIfEdited())},
                {"NewClassTypeIfEdited", nullSafe(row.getNewClassTypeIfEdited())},
                {"DuplicateVariantAfterCreate", nullSafe(row.getDuplicateVariantAfterCreate())},
        };
        ExtentLogger.infoLabeledTable("Test Data — " + row.getTestCaseId(), table);
        Log.info("[AccountClassLifecycle] Running {} — className(excel)='{}' resolved='{}' type='{}'",
                row.getTestCaseId(), row.getClassName(), resolvedClassName, row.getClassType());
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
