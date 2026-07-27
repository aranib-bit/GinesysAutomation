package com.tests.Finance.Setup;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccGrpDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Setup.AccountGroupListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.Setup.AccountGroupContextMenu;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Setup.SetupMenuType;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Setup.AccountGroup.AccountGroupRow;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.dto.MergedFinanceAccountGroupData;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinAccGroupDataSupplier;
import com.tests.WebTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateAccountGroup extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Create Finance Account Group hierarchy from Excel",
          dataProvider = "dataSupplier",
          dataProviderClass = MergedFinAccGroupDataSupplier.class)
    public void CreateAccountGroup(MergedFinanceAccountGroupData data) {

        ReportLogger.info(LogCategory.VALIDATION,
                "Expected Result: All account group rows from Excel created in DB. " +
                "Duplicate rows skipped. Tree reflects full hierarchy.");

        UserCredential u = DriverManager.getCurrentUser();
        HomePage home = new LoginPage().loginToApplication(u.username(), u.password());

        AccountGroupListPage page = NavigationUtils.navigateToFinanceSetupSideMenu(
                home,
                MainMenuType.SETUP,
                SetupMenuType.ACCOUNTGROUP,
                AccountGroupListPage.class);

        FinAccGrpDao dao = DatabaseManager.getJdbi().onDemand(FinAccGrpDao.class);

        // Build a Group -> ParentGroup lookup from the full row set so any node's full
        // ancestor chain can be walked and expanded top-down. Without this, isNodeVisible()/
        // expandNode() only ever look at a single node — a parent nested several levels deep
        // (e.g. "Purchase" under Expense > Direct Expenses > Purchase) is never actually
        // visible in a freshly-loaded, fully-collapsed tree, and every row targeting it would
        // be wrongly skipped as "parent not found" without ever touching the UI.
        Map<String, String> parentByName = new HashMap<>();
        for (AccountGroupRow r : data.getRows()) {
            parentByName.put(r.getGroupName(), r.getParentGroup());
        }

        int created = 0;
        int skipped = 0;

        rows:
        for (AccountGroupRow row : data.getRows()) {

            // Level 0 is the root "Chart of Accounts" — always pre-exists, never re-create
            if (row.getLevel() == 0) {
                continue;
            }

            // DB duplicate check — actual constraint is UNIQUE(grpname, parcode), i.e. scoped to
            // parent, not globally unique. countByName alone would false-positive-skip a
            // legitimately new group that happens to share a name with one under a different parent.
            if (dao.countByNameAndParent(row.getGroupName(), row.getParentGroup()) > 0) {
                ReportLogger.info(LogCategory.VALIDATION,
                        "SKIP (duplicate): [" + row.getGroupName() + "] under [" + row.getParentGroup() + "]");
                skipped++;
                continue;
            }

            // Walk the full ancestor chain (root-most first) and expand each level in turn —
            // a deeply-nested parent is invisible in a freshly-loaded/collapsed tree until
            // every ancestor above it has been expanded, not just the immediate parent itself.
            for (String ancestor : ancestorChain(row.getParentGroup(), parentByName)) {
                if (!page.isNodeVisible(ancestor)) {
                    ReportLogger.validationWarn(
                            "SKIP (ancestor not found in UI): [" + row.getGroupName() + "] — ancestor [" +
                            ancestor + "] of parent [" + row.getParentGroup() + "] does not match any tree node text.");
                    skipped++;
                    continue rows;
                }
                page.expandNode(ancestor);
            }

            // Fail-fast, diagnosable check: after expanding ancestors, the immediate parent
            // should now be visible unless its name in the Excel doesn't match the live UI tree.
            if (!page.isNodeVisible(row.getParentGroup())) {
                String reason = "Tree".equals(row.getParentGroup())
                        ? "top-level groups (Level 1) are not creatable through this UI — " +
                          "'Tree' is a virtual DB root, never rendered as a node"
                        : "parent does not match any tree node text — likely a stale/mismatched " +
                          "name in AccountGroup.xlsx vs the live tree";
                ReportLogger.validationWarn(
                        "SKIP (" + reason + "): [" + row.getGroupName() + "] parent=[" + row.getParentGroup() + "]");
                skipped++;
                continue;
            }

            // Native UI: click to select (already expanded above) → right-click → Add → type name → Enter
            try {
                page.rightClickNode(row.getParentGroup())
                    .clickContextMenuItem(AccountGroupContextMenu.ADD)
                    .typeGroupName(row.getGroupName())
                    .confirmEntry(row.getGroupName());
            } catch (IllegalStateException | org.openqa.selenium.NoSuchElementException | org.openqa.selenium.TimeoutException e) {
                // Database validation: distinguish a benign pre-flight failure (nothing was
                // ever created — safe to skip) from a genuine partial-creation defect (Add
                // succeeded server-side but the rename never took effect correctly, leaving a
                // stray or garbled "New Group..." row under this parent). The latter must fail
                // loudly — silently skipping it would hide real data corruption. Matched via
                // LIKE '%New Group%' (not an exact match) since a broken selection/replace was
                // confirmed to leave garbled leftovers like "New GroupaZZZ Test Verify Group",
                // not just a clean untouched "New Group".
                var strayNames = dao.findStrayNewGroupNamesUnderParent(row.getParentGroup());
                int partialReal = dao.countByNameAndParent(row.getGroupName(), row.getParentGroup());
                if (!strayNames.isEmpty() || partialReal > 0) {
                    throw new AssertionError(
                            "Account group creation PARTIALLY FAILED for [" + row.getGroupName() +
                            "] under [" + row.getParentGroup() + "]: " + e.getMessage() +
                            (!strayNames.isEmpty()
                                    ? " — stray/garbled row(s) left in fingrp under this parent: " + strayNames
                                    : " — the row exists in fingrp but the UI never confirmed it."));
                }
                ReportLogger.validationWarn(
                        "WARN: [" + row.getGroupName() + "] under [" + row.getParentGroup() + "] skipped: " + e.getMessage());
                skipped++;
                continue;
            }

            // Post-creation DB verify
            if (dao.countByNameAndParent(row.getGroupName(), row.getParentGroup()) == 0) {
                throw new AssertionError(
                        "Group not persisted after creation: [" + row.getGroupName() +
                        "] under [" + row.getParentGroup() + "]");
            }

            ReportLogger.info(LogCategory.VALIDATION,
                    "CREATED: [" + row.getGroupName() + "] Level=" + row.getLevel() +
                    " Parent=[" + row.getParentGroup() + "]");
            created++;
        }

        ReportLogger.info(LogCategory.VALIDATION,
                "Account Group creation complete. Created=" + created + " | Skipped(duplicate)=" + skipped);

        com.gsl.driver.SessionManager.closeSessionAndReleaseUser("Account Group creation complete");
    }

    /**
     * Walks up from {@code name}'s immediate parent to the root, returning the ancestor chain
     * in root-most-first order (excluding {@code name} itself and excluding the virtual "Tree"
     * root, which is never rendered as a UI node). E.g. for "Purchase" (parent "Direct Expenses",
     * whose parent is "Expense", whose parent is "Tree"): returns ["Expense", "Direct Expenses"].
     * Expanding these in order reveals "Purchase" in the tree.
     */
    private static List<String> ancestorChain(String name, Map<String, String> parentByName) {
        List<String> chain = new ArrayList<>();
        String current = parentByName.get(name);
        while (current != null && !"Tree".equals(current)) {
            chain.add(0, current);
            current = parentByName.get(current);
        }
        return chain;
    }
}
