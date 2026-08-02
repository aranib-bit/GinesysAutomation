package com.gsl.reports;

import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;

/**
 * Builds a human-readable English sentence describing one AccountGroupLifecycle row, following
 * the same house style as {@link ArVoucherDescriptionBuilder}/{@link ApVoucherDescriptionBuilder} —
 * a short {@code [idx/total] — GroupName} title stays as the scannable node name, while this
 * sentence goes into {@code ExtentReport.setTestDescription(...)} for full context.
 *
 * <p>Sample output:
 * <pre>
 *   "Verify that account group Petty Cash Fund can be created under parent Purchase
 *    (category: Smoke, test case AGL-001) and is created successfully — no further
 *    edit or delete expected."
 * </pre>
 */
public final class AccountGroupLifecycleDescriptionBuilder {

    private AccountGroupLifecycleDescriptionBuilder() {}

    /**
     * @param row         the data row
     * @param displayName log-safe display form of the group name (e.g. truncated/blank-labeled)
     */
    public static String build(AccountGroupLifecycleRow row, String displayName) {
        if (row == null) return null;

        StringBuilder sb = new StringBuilder("Verify that account group ")
                .append(displayName)
                .append(" can be created under parent ")
                .append(row.getParentGroup());

        String category = row.getNamingCategory();
        String tcId = row.getTestCaseId();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasTcId = tcId != null && !tcId.isBlank();
        if (hasCategory || hasTcId) {
            sb.append(" (");
            if (hasCategory) sb.append("category: ").append(category);
            if (hasCategory && hasTcId) sb.append(", ");
            if (hasTcId) sb.append("test case ").append(tcId);
            sb.append(")");
        }

        if ("REJECT".equalsIgnoreCase(row.getExpectedOutcome())) {
            sb.append(", and is expected to be correctly rejected as a duplicate — no stray row should remain.");
            return sb.toString();
        }

        sb.append(" and is created successfully");

        boolean edited = "Y".equalsIgnoreCase(row.getEditAfterCreate());
        boolean deleted = "Y".equalsIgnoreCase(row.getDeleteAfterCreate());
        if (edited && deleted) {
            sb.append(", then renamed to ").append(row.getNewNameIfEdited())
              .append(", then deleted after creation.");
        } else if (edited) {
            sb.append(", then renamed to ").append(row.getNewNameIfEdited()).append(".");
        } else if (deleted) {
            sb.append(", then deleted after creation.");
        } else {
            sb.append(" — no further edit or delete expected.");
        }
        return sb.toString();
    }
}
