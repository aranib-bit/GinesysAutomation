package com.tests;

import com.annotations.FrameworkAnnotation;
import com.enums.TestAuthor;
import com.pages.HomePage;
import com.pages.LoginPage;
import com.testdata.TestData;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest{

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To check whether the title of the orangehrm website homepage is displayed correctly", dataProvider = "getData",dataProviderClass = com.utils.dataprovider.DataProviderUtils.class)

    public void loginTest(TestData testData) throws InterruptedException {


        LoginPage loginPage=new LoginPage();
        String actualTitle =loginPage.loginToApplication(testData.username,testData.password).getHomePageTitle();
        Assert.assertEquals(actualTitle, testData.expectedTitle);

        HomePage homePage=new HomePage();
        homePage.clickOnAdmin();

        //DriverManager.getDriver().findElement(By.xpath("//a[span[text()='Admin']]")).click();

    }
}
