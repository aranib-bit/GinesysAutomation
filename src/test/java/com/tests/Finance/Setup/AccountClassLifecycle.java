package com.tests.Finance.Setup;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccClassDao;
import com.gsl.driver.DriverManager;
import com.gsl.driver.SessionDownloadDirectory;
import com.gsl.driver.SessionManager;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Setup.AccountClassListPage;
import com.gsl.pages.Finance.Setup.AddAccountClassPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Setup.SetupMenuType;
import com.gsl.reports.AccountClassExtentDescriptionLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.testdata.Finance.Setup.AccountClass.AccountClassLifecycleRow;
import com.gsl.testdata.UserCredential;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.TestDataUniqueifier;
import com.gsl.utils.dataprovider.MergedAccountClassLifecycleDataSupplier;
import com.gsl.utils.io.DownloadUtils;
import com.gsl.utils.io.ExportedExcelReader;
import com.tests.WebTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.gsl.validation.db.finance.AccountClassDbContextBuilder;
import com.gsl.validation.db.finance.FinAccClassDbValidator;

/**
 * Account Class (Finance → Setup → Chart of Accounts → Account Class) — naming-convention Create
 * scenarios, Edit, dropdown fixed-values check, Export-to-Excel, and DB-driven boundary-length
 * check. One Excel row = one independent parallel invocation, same {@code runInParallel=true} /
 * {@code SessionPool} model AP Voucher uses, so rows can interleave with AP Voucher rows in the
 * same suite run.
 */
public class AccountClassLifecycle extends WebTest {

    private static final List<String> EXPECTED_CLASS_TYPES = List.of(
            "Customer", "Supplier", "Transporter", "Jobber", "Agent",
            "TDS Agency", "Employee", "Others", "Channel");

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Account Class naming-convention scenario",
            dataProvider = "AccountClassLifecycleData",
            dataProviderClass = MergedAccountClassLifecycleDataSupplier.class)
    public void accountClassScenario(UserCredential user, AccountClassLifecycleRow row) {

        SoftAssert softAssert = new SoftAssert();

        boolean exemptFromUniqueify = isBlankOrWhitespace(row.getClassName())
                || "Customer".equalsIgnoreCase(row.getClassName());
        String resolvedName = exemptFromUniqueify
                ? row.getClassName()
                : TestDataUniqueifier.uniqueify(row.getClassName());

        // Test data logged before Step 1 — same convention as ApVoucherExtentDescriptionLogger.
        AccountClassExtentDescriptionLogger.log(row, resolvedName);

        final HomePage[] homeRef = new HomePage[1];
        final AccountClassListPage[] listRef = new AccountClassListPage[1];
        final AddAccountClassPage[] addRef = new AddAccountClassPage[1];
        final String[] resolvedNewNameRef = new String[1];
        final String[] duplicateVariantNameRef = new String[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
            ReportLogger.business("Logged in as: " + u.username());
        });

        StepExecutor.step("Open Finance → Account Class List", () -> {
            listRef[0] = NavigationUtils.navigateToFinanceSetupSideMenu(
                    homeRef[0], MainMenuType.SETUP, SetupMenuType.ACCOUNTCLASS, AccountClassListPage.class);
            ReportLogger.business("Account Class list page opened");
        });

        StepExecutor.step("Add Account Class", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .typeClassName(resolvedName)
                    .selectClassType(row.getClassType())
                    .clickSave()
                    .dismissSaveErrorIfPresent();
            ReportLogger.business("Submitted Class Name='" + resolvedName + "', Class Type='" + row.getClassType() + "'");
        });

        // Confirmed live: Save does NOT leave the popup open/reset for immediate reuse — it must
        // be explicitly closed (Close -> "Discard changes?" -> Yes) before the list's Add button
        // or grid rows are interactable again for the next cycle (duplicate-variant retry or Edit).
        StepExecutor.step("Close Account Class Page (after Add)", () -> {
            listRef[0] = addRef[0].clickClose().closeAccountClass();
            ReportLogger.business("Account Class popup closed, returned to list");
        });

        String duplicateVariant = row.getDuplicateVariantAfterCreate();
        if (duplicateVariant != null && !duplicateVariant.isBlank()) {
            StepExecutor.step("Duplicate Variant Check", () -> {
                String variantName = "CASE".equalsIgnoreCase(duplicateVariant)
                        ? resolvedName.toUpperCase(Locale.ROOT)
                        : resolvedName;
                duplicateVariantNameRef[0] = variantName;
                addRef[0] = listRef[0].clickAdd()
                        .typeClassName(variantName)
                        .selectClassType(row.getClassType())
                        .clickSave()
                        .dismissSaveErrorIfPresent();
                ReportLogger.business("Attempted duplicate-variant Class Name='" + variantName + "' — expecting rejection");
            });

            StepExecutor.step("Close Account Class Page (after Duplicate Variant Check)", () -> {
                listRef[0] = addRef[0].clickClose().closeAccountClass();
                ReportLogger.business("Account Class popup closed, returned to list");
            });
        }

        if ("Y".equalsIgnoreCase(row.getEditAfterCreate())) {
            StepExecutor.step("Edit Account Class", () -> {
                String newBase = row.getNewClassNameIfEdited();
                String newResolved = (newBase == null || newBase.isBlank())
                        ? null : TestDataUniqueifier.uniqueify(newBase);
                resolvedNewNameRef[0] = newResolved;

                addRef[0] = listRef[0].selectGridRow(resolvedName).clickEdit();
                if (newResolved != null) {
                    addRef[0].typeClassName(newResolved);
                }
                String newType = row.getNewClassTypeIfEdited();
                if (newType != null && !newType.isBlank()) {
                    addRef[0].selectClassType(newType);
                }
                addRef[0] = addRef[0].clickSave().dismissSaveErrorIfPresent();
                ReportLogger.business("Edited Class Name -> '" + newResolved + "', Class Type -> '" + newType + "'");
            });

            StepExecutor.step("Close Account Class Page (after Edit)", () -> {
                listRef[0] = addRef[0].clickClose().closeAccountClass();
                ReportLogger.business("Account Class popup closed, returned to list");
            });
        }

        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));

        StepExecutor.step("Database Validation", () -> {
            new FinAccClassDbValidator().validate(AccountClassDbContextBuilder.build(
                    row, resolvedName, resolvedNewNameRef[0], duplicateVariantNameRef[0]));
            ReportLogger.pass("DB validated. Class Name: " + resolvedName);
        });

        StepExecutor.step("Finalize assertions", softAssert::assertAll);
    }

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Verify Account Class Type dropdown contains exactly the fixed set of options")
    public void verifyClassTypeDropdownFixedValues() {
        final HomePage[] homeRef = new HomePage[1];
        final AccountClassListPage[] listRef = new AccountClassListPage[1];
        final AddAccountClassPage[] addRef = new AddAccountClassPage[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
        });

        StepExecutor.step("Open Finance → Account Class List", () ->
                listRef[0] = NavigationUtils.navigateToFinanceSetupSideMenu(
                        homeRef[0], MainMenuType.SETUP, SetupMenuType.ACCOUNTCLASS, AccountClassListPage.class));

        StepExecutor.step("Verify Class Type options (1st Add)", () -> {
            addRef[0] = listRef[0].clickAdd();
            List<String> options = addRef[0].getClassTypeOptions();
            assertSameOptions(options);
        });

        StepExecutor.step("Close and reopen to exercise dropdown-reopen quirk", () -> {
            listRef[0] = addRef[0].clickClose().closeAccountClass();
            addRef[0] = listRef[0].clickAdd();
            List<String> options = addRef[0].getClassTypeOptions();
            assertSameOptions(options);
        });

        StepExecutor.step("Close Account Class Page", () ->
                listRef[0] = addRef[0].clickClose().closeAccountClass());

        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));
    }

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Export Account Class list to Excel and verify the downloaded file")
    public void exportAccountClassToExcel() {
        FinAccClassDao dao = DatabaseManager.getJdbi().onDemand(FinAccClassDao.class);
        String resolvedName = TestDataUniqueifier.uniqueify("Export Check Class");
        String classType = "Others";

        final HomePage[] homeRef = new HomePage[1];
        final AccountClassListPage[] listRef = new AccountClassListPage[1];
        final AddAccountClassPage[] addRef = new AddAccountClassPage[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
        });

        StepExecutor.step("Open Finance → Account Class List", () ->
                listRef[0] = NavigationUtils.navigateToFinanceSetupSideMenu(
                        homeRef[0], MainMenuType.SETUP, SetupMenuType.ACCOUNTCLASS, AccountClassListPage.class));

        StepExecutor.step("Create class to export", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .typeClassName(resolvedName)
                    .selectClassType(classType)
                    .clickSave()
                    .dismissSaveErrorIfPresent();
            listRef[0] = addRef[0].clickClose().closeAccountClass();
        });

        StepExecutor.step("Database Validation (pre-export)", () -> {
            boolean present = dao.findByName(resolvedName).isPresent();
            Assert.assertTrue(present, "Export-check class '" + resolvedName + "' was not created.");
        });

        final Path[] downloadedFileRef = new Path[1];
        StepExecutor.step("Export to Excel", () -> {
            listRef[0].clickExportToExcel();
            Path downloadDir = SessionDownloadDirectory.current();
            downloadedFileRef[0] = DownloadUtils.waitForDownloadedFile(downloadDir, "class", 30);
        });

        StepExecutor.step("Verify exported file content", () -> {
            List<Map<String, String>> rows = ExportedExcelReader.readRows(downloadedFileRef[0]);
            boolean found = rows.stream().anyMatch(r ->
                    resolvedName.equals(ExportedExcelReader.get(r, "Class Name"))
                    && classType.equalsIgnoreCase(ExportedExcelReader.get(r, "Class Type")));
            Assert.assertTrue(found, "Exported file did not contain a row for Class Name='"
                    + resolvedName + "', Class Type='" + classType + "'. Rows read: " + rows.size());
            ReportLogger.pass("Exported file verified: '" + resolvedName + "' / '" + classType + "'");
        });

        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));
    }

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "Account Class Name boundary length, driven by the live admcls.clsname max length")
    public void classNameBoundaryLength() {
        FinAccClassDao dao = DatabaseManager.getJdbi().onDemand(FinAccClassDao.class);
        int maxLength = dao.getClassNameMaxLength();
        String maxLengthName = TestDataUniqueifier.uniqueifyToExactLength(maxLength);
        String overflowName = TestDataUniqueifier.uniqueifyToExactLength(maxLength + 1);
        String classType = "Others";

        final HomePage[] homeRef = new HomePage[1];
        final AccountClassListPage[] listRef = new AccountClassListPage[1];
        final AddAccountClassPage[] addRef = new AddAccountClassPage[1];

        StepExecutor.step("Login to Application", () -> {
            UserCredential u = DriverManager.getCurrentUser();
            homeRef[0] = SessionManager.ensureLoggedIn(u);
        });

        StepExecutor.step("Open Finance → Account Class List", () ->
                listRef[0] = NavigationUtils.navigateToFinanceSetupSideMenu(
                        homeRef[0], MainMenuType.SETUP, SetupMenuType.ACCOUNTCLASS, AccountClassListPage.class));

        StepExecutor.step("Add at exact max length", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .typeClassName(maxLengthName)
                    .selectClassType(classType)
                    .clickSave()
                    .dismissSaveErrorIfPresent();
            listRef[0] = addRef[0].clickClose().closeAccountClass();
        });

        StepExecutor.step("DB Validate — exact max length created", () -> {
            boolean present = dao.findByName(maxLengthName).isPresent();
            Assert.assertTrue(present, "Exact-max-length Class Name (" + maxLength
                    + " chars) was not created: '" + maxLengthName + "'");
        });

        StepExecutor.step("Add at max length + 1 (over the limit)", () -> {
            addRef[0] = listRef[0].clickAdd()
                    .typeClassName(overflowName)
                    .selectClassType(classType)
                    .clickSave()
                    .dismissSaveErrorIfPresent();
            listRef[0] = addRef[0].clickClose().closeAccountClass();
        });

        StepExecutor.step("DB Validate — over-limit outcome (reject or truncate)", () -> {
            // ⚠️ UNVERIFIED: whether the app rejects, truncates, or client-blocks a name past the
            // live max length. This asserts NO row exists with the full overflow string — if the
            // app instead truncates to maxLength and saves that, adjust this assertion once
            // confirmed on the first live run rather than assuming rejection.
            int overflowCount = dao.countByName(overflowName);
            Assert.assertEquals(overflowCount, 0,
                    "Expected the over-limit Class Name (" + (maxLength + 1)
                    + " chars) to be rejected or truncated, but found an exact-match row.");
        });

        StepExecutor.step("Logout (deferred)", () ->
                ReportLogger.session("Logout deferred — session may be reused by a later test on this thread."));
    }

    private static void assertSameOptions(List<String> actualOptions) {
        Assert.assertEquals(actualOptions.size(), EXPECTED_CLASS_TYPES.size(),
                "Class Type dropdown option count mismatch. Actual: " + actualOptions);
        for (String expected : EXPECTED_CLASS_TYPES) {
            Assert.assertTrue(actualOptions.contains(expected),
                    "Expected Class Type option '" + expected + "' not found. Actual: " + actualOptions);
        }
    }

    private static boolean isBlankOrWhitespace(String s) {
        return s == null || s.isBlank();
    }
}
