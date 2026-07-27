package com.tests.Finance.Setup;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccGrpDao;
import com.gsl.driver.DriverManager;
import com.gsl.driver.FailureClassifier;
import com.gsl.driver.FailureClassifier.FailureCategory;
import com.gsl.driver.SessionManager;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Setup.AccountGroupListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.Setup.AccountGroupContextMenu;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Setup.SetupMenuType;
import com.gsl.reports.AccountGroupLifecycleDescriptionBuilder;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ExtentReport;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupLifecycleRow;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedAccountGroupLifecycleData;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedAccountGroupLifecycleDataSupplier;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Finance Account Group lifecycle: Create (~50 categorized-name groups) → Edit (flagged
 * subset) → Delete (flagged subset), each with DB validation. Each group is reported as its
 * own top-level Extent test case (via {@link ExtentReport#createTest(String)}), matching the
 * AP/AR Voucher report convention — not nested as a collapsible child under one shared test.
 */
public class AccountGroupLifecycle extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Create/Edit/Delete Finance Account Group lifecycle from Excel",
          dataProvider = "dataSupplier",
          dataProviderClass = MergedAccountGroupLifecycleDataSupplier.class)
    public void accountGroupLifecycle(MergedAccountGroupLifecycleData data) {

        FinAccGrpDao dao = DatabaseManager.getJdbi().onDemand(FinAccGrpDao.class);
        final AccountGroupListPage[] pageRef = new AccountGroupListPage[1];

        // Capture the method-level test entry (created by TestListener.onTestStart) so
        // Login/Navigate/Summary/Logout report under it, separately from the per-row entries.
        ExtentTest methodTest = ExtentManager.getExtentTest();

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            HomePage home = SessionManager.ensureLoggedIn(u);
            pageRef[0] = NavigationUtils.navigateToFinanceSetupSideMenu(
                    home, MainMenuType.SETUP, SetupMenuType.ACCOUNTGROUP, AccountGroupListPage.class);
        });

        int idx = 0;
        int total = data.getRows().size();
        int created = 0, rejected = 0, edited = 0, deleted = 0;
        List<String> failedRows = new ArrayList<>();

        for (AccountGroupLifecycleRow row : data.getRows()) {
            idx++;

            // Separate top-level Extent test-case entry for this group — NOT a nested child.
            ExtentReport.createTest("[" + idx + "/" + total + "] — " + describeName(row.getGroupName()));
            ExtentReport.setTestDescription(
                    AccountGroupLifecycleDescriptionBuilder.build(row, describeName(row.getGroupName())));
            ExtentTest rowTest = ExtentManager.getExtentTest();

            AccountGroupListPage page = pageRef[0];

            try {
                StepExecutor.stepUnder(rowTest, "Expand ancestors", () -> {
                    for (String ancestor : dao.getAncestorChain(row.getParentGroup())) {
                        if (!page.isNodeVisible(ancestor)) {
                            throw new IllegalStateException(
                                    "Ancestor [" + ancestor + "] of parent [" + row.getParentGroup()
                                    + "] does not match any tree node text.");
                        }
                        page.expandNode(ancestor);
                    }
                    if (!page.isNodeVisible(row.getParentGroup())) {
                        throw new IllegalStateException(
                                "Parent [" + row.getParentGroup() + "] does not match any tree node text.");
                    }
                });

                if ("REJECT".equals(row.getExpectedOutcome())) {
                    StepExecutor.stepUnder(rowTest, "Create (expect rejection)", () -> {
                        boolean serverRejected;
                        try {
                            page.rightClickNode(row.getParentGroup())
                                .clickContextMenuItem(AccountGroupContextMenu.ADD)
                                .typeGroupName(row.getGroupName())
                                .confirmEntry(row.getGroupName());
                            serverRejected = false;
                        } catch (IllegalStateException | org.openqa.selenium.NoSuchElementException
                                | org.openqa.selenium.TimeoutException e) {
                            serverRejected = true;
                        }
                        List<String> strayNames = dao.findStrayNewGroupNamesUnderParent(row.getParentGroup());
                        if (!serverRejected || !strayNames.isEmpty()) {
                            throw new AssertionError(
                                    "Expected REJECT for [" + describeName(row.getGroupName()) + "] under ["
                                    + row.getParentGroup() + "] but " +
                                    (!serverRejected ? "the group was created successfully."
                                                     : "stray row(s) were left behind: " + strayNames));
                        }
                        ReportLogger.pass("Correctly rejected: [" + describeName(row.getGroupName())
                                + "] — no stray row left in fingrp.");
                    });
                    rejected++;
                    continue;
                }

                StepExecutor.stepUnder(rowTest, "Create", () ->
                        page.rightClickNode(row.getParentGroup())
                            .clickContextMenuItem(AccountGroupContextMenu.ADD)
                            .typeGroupName(row.getGroupName())
                            .confirmEntry(row.getGroupName()));

                StepExecutor.stepUnder(rowTest, "DB Validate Create", () -> {
                    if (dao.countByNameAndParent(row.getGroupName(), row.getParentGroup()) == 0) {
                        throw new AssertionError(
                                "Group not persisted after creation: [" + row.getGroupName()
                                + "] under [" + row.getParentGroup() + "]");
                    }
                    ReportLogger.pass("CREATED: [" + row.getGroupName() + "] under [" + row.getParentGroup() + "]");
                });
                created++;

                String currentName = row.getGroupName();

                if ("Y".equalsIgnoreCase(row.getEditAfterCreate())) {
                    String newName = row.getNewNameIfEdited();
                    StepExecutor.stepUnder(rowTest, "Edit (rename to " + newName + ")",
                            () -> page.editGroupName(row.getGroupName(), newName));
                    StepExecutor.stepUnder(rowTest, "DB Validate Edit", () -> {
                        if (dao.countByNameAndParent(row.getGroupName(), row.getParentGroup()) > 0) {
                            throw new AssertionError("Old name still present after edit: [" + row.getGroupName() + "]");
                        }
                        if (dao.countByNameAndParent(newName, row.getParentGroup()) == 0) {
                            throw new AssertionError("Renamed group not persisted: [" + newName + "]");
                        }
                        ReportLogger.pass("EDITED: [" + row.getGroupName() + "] -> [" + newName + "]");
                    });
                    currentName = newName;
                    edited++;
                }

                if ("Y".equalsIgnoreCase(row.getDeleteAfterCreate())) {
                    final String nameToDelete = currentName;
                    StepExecutor.stepUnder(rowTest, "Delete", () -> page.deleteNode(nameToDelete));
                    StepExecutor.stepUnder(rowTest, "DB Validate Delete", () -> {
                        if (dao.countByNameAndParent(nameToDelete, row.getParentGroup()) > 0) {
                            throw new AssertionError("Group still present after delete: [" + nameToDelete + "]");
                        }
                        ReportLogger.pass("DELETED: [" + nameToDelete + "]");
                    });
                    deleted++;
                }

            } catch (RuntimeException e) {
                // A genuine navigation/session/browser problem (stuck dialog, dead session, crashed
                // driver) means the page is no longer in a reliable state — swallowing it here would
                // hand a broken session to whichever test runs next on this thread (WebTest.tearDown()
                // only recovers on NAVIGATION_FAILURE/SESSION_FAILURE/BROWSER_FAILURE, never on the
                // plain AssertionError this method throws at the end). Let it propagate so tearDown's
                // existing FailureClassifier/RecoveryManager path forces a fresh session next time,
                // instead of laundering it into a business-failure that leaves the bad state in place.
                FailureCategory category = FailureClassifier.classify(e);
                if (category == FailureCategory.NAVIGATION_FAILURE
                        || category == FailureCategory.SESSION_FAILURE
                        || category == FailureCategory.BROWSER_FAILURE) {
                    throw e;
                }
                // StepExecutor already logged/screenshotted the failing step and marked this
                // row's own top-level entry red. Record it and continue to the next row so one
                // business/DB-validation failure doesn't abort the remaining rows.
                failedRows.add(row.getGroupName() + " (" + e.getMessage() + ")");
            }
        }

        // Restore the method-level entry for the closing steps.
        ExtentManager.setExtentTest(methodTest);
        final int createdF = created, rejectedF = rejected, editedF = edited, deletedF = deleted;
        StepExecutor.step("Account Group Lifecycle Summary", () ->
                ReportLogger.pass("Created=" + createdF + " | Rejected(expected)=" + rejectedF
                        + " | Edited=" + editedF + " | Deleted=" + deletedF
                        + " | Failed=" + failedRows.size()
                        + (failedRows.isEmpty() ? "" : " | FailedRows=" + failedRows)));
        // Logout is deferred, not performed here: the session may be reused by another test on
        // this thread (see SessionManager/WebTest). The real logout+driver-quit happens once,
        // either via RecoveryManager (only for a genuine session/browser failure) or at end-of-block
        // cleanup in TestListener.onFinish(ITestContext).
        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        Assert.assertTrue(failedRows.isEmpty(), "Rows failed: " + failedRows);
    }

    /** Short, log-safe description for names that are empty/whitespace/very long. */
    private static String describeName(String name) {
        if (name == null || name.isBlank()) return "\"\" (empty)";
        if (name.length() > 40) return name.substring(0, 40) + "...(" + name.length() + " chars)";
        return name;
    }

}
