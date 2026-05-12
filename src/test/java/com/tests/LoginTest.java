package com.tests;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.pagecomponent.Admin.AdminHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.testdata.MasterTestData;
import com.gsl.utils.dataprovider.MergedReceiptDataSupplier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoginTest extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To check whether the user lands on the correct dashboard after login",
          dataProvider = "getData", dataProviderClass = MergedReceiptDataSupplier.class)
    public void loginAndVerifyDashboardTest(MasterTestData masterTestData) throws InterruptedException {

        LoginPage loginPage = new LoginPage();
        Object dashboardPage = loginPage.loginToApplication("ab", "ab");

//        // Now, you can check which dashboard you landed on
//        if (dashboardPage instanceof AdminHomePage) {
//            AdminHomePage adminHomePage = (AdminHomePage) dashboardPage;
//            String actual = adminHomePage.getAdminTopMenuComponent().getSelectedMenu();
//            Assert.assertEquals(actual, "Dashboard", "Not landed on the Admin dashboard");
//
//        } else if (dashboardPage instanceof InventoryHomePage) {
//            InventoryHomePage inventoryHomePage = (InventoryHomePage) dashboardPage;
//            String actual = inventoryHomePage.getInventoryTopMenuComponent().getSelectedMenu();
//            Assert.assertEquals(actual, "Dashboard", "Not landed on the Inventory dashboard");
//
//        } else {
//            Assert.fail("Landed on an unknown page after login.");
//        }


        HomePage homePage=new HomePage();
        homePage.logoutFromApplication();

    }
}
