package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.tests.BaseTest;
import org.testng.annotations.Test;




public class createArVoucher extends BaseTest {
    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new AR Voucher of Finance from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)

    public void CreateArVoucher(MergedFinanceArVoucherData data) throws InterruptedException {
        System.out.println("Create AR Voucher of Finance from UI");
        System.out.println("Running test for: " + data.getMasterTestData().getTestcaseid());

        // 1. Login to the application
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(data.getMasterTestData().getUsername(), data.getMasterTestData().getPassword());

        // 2. Use the navigation utility to get to the correct screen
        ArVoucherListPage arvoucherListPage = NavigationUtils.navigateToFinanceSideMenu(homePage,
                MainMenuType.OPERATIONS,
                OperationsMenuType.ARVOUCHER, ArVoucherListPage.class);

        arvoucherListPage.clickAdd().clickCashBankSearchIcon().
                selectCashBank("Punjab National Bank-214")
                .setsubledgername("ABUL KASHEM MOLLHA")
                .setSubLedgerAmount("4.00")
                .clickAdjust();


    }

}
