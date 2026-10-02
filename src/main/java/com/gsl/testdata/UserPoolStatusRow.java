package com.gsl.testdata;

/**
 * One row of the Extent Report's "User Pool Status" table — see
 * {@code TestListener#onTestStart}, which joins {@link UserPool#allUsernamesOfType} (the full
 * roster) with {@code SessionPool#currentTestCaseIdsByUsername()} (the live, reuse-aware "which
 * test case is using this user right now" signal) to build these rows fresh at the start of
 * every test.
 */
public record UserPoolStatusRow(String username, String status, String testCaseId) {

    public static final String USED = "Used";
    public static final String UNUSED = "Unused";
}
