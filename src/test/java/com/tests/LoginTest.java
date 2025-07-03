package com.tests;

import com.pages.HomePage;
import com.pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest{

    @DataProvider
    public Object[][] getData() {
        return new Object[][]{
                {"Admin","admin123","OrangeHRM"},
                {"Admin","admin1234","OrangeHRM"}
        };
    }

    @Test(description = "To check whether the title of the orangehrm website homepage is displayed correctly", dataProvider = "getData")

    public void loginTest(String username,String password,String expectedTitle) throws InterruptedException {


        LoginPage loginPage=new LoginPage();
        String actualTitle =loginPage.loginToApplication(username,password).getHomePageTitle();
        Assert.assertEquals(actualTitle, expectedTitle);

        HomePage homePage=new HomePage();
        homePage.clickOnAdmin();

        //DriverManager.getDriver().findElement(By.xpath("//a[span[text()='Admin']]")).click();

    }
}
