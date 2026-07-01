package com.tests.Finance.Setup;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinAccGrpDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Setup.AccountGroupListPage;
import com.gsl.pages.HomePage;
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

        int created = 0;
        int skipped = 0;

        for (AccountGroupRow row : data.getRows()) {

            // Level 0 is the root "Chart of Accounts" — always pre-exists, never re-create
            if (row.getLevel() == 0) {
                continue;
            }

            // DB duplicate check — no JS tree scan
            if (dao.countByNameAndParent(row.getGroupName(), row.getParentGroup()) > 0) {
                ReportLogger.info(LogCategory.VALIDATION,
                        "SKIP (duplicate): [" + row.getGroupName() + "] under [" + row.getParentGroup() + "]");
                skipped++;
                continue;
            }

            // Native UI: expand parent → right-click → Add → type name → Enter
            page.expandNode(row.getParentGroup())
                .rightClickNode(row.getParentGroup())
                .clickAdd()
                .typeGroupName(row.getGroupName())
                .confirmEntry();

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

        new HomePage().logoutFromApplication();
    }
}
