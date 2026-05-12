package com.tests.Inventory.Product_Definition.Setup;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.HomePage;
import com.gsl.pages.Inventory.Product_Definition.CategoryListPage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.testdata.Inventory.MergedInventoryItemData;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedInvItemDataSupplier;
import com.tests.WebTest;
import org.testng.annotations.Test;

public class CreateCategory extends WebTest {
    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new category of Inventory Item from UI")
    public void createCategory(){
        System.out.println("Inside createCategory method");
        // 1. Login
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(
                "aranibarman",
                "ab"
        );

        // 2. Navigate to Item List
        CategoryListPage categoryListPage = NavigationUtils.navigateToInventorySideMenu(
                homePage,
                MainMenuType.PRODUCT_DEFINITION,
                Product_DefintionMenuType.CATEGORY,
                CategoryListPage.class
        );

        categoryListPage.clickAdd();


    }
}
