package com.tests;

import com.annotations.FrameworkAnnotation;
import com.driver.DriverManager;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PaymentTest extends BaseTest {

    @FrameworkAnnotation()
    @Test(description = "To check whether the title of the orangehrm website homepage is displayed correctly")
    public void loginTest() throws InterruptedException {

        Thread.sleep(10000);
        DriverManager.getDriver().findElement(By.name("username")).sendKeys("Admin");
        DriverManager.getDriver().findElement(By.name("password")).sendKeys("admin123");
        DriverManager.getDriver().findElement(By.xpath("//*[@type='submit']")).click();
        Thread.sleep(5000);
        String actualTitle = DriverManager.getDriver().getTitle();
        String expectedTitle = "OrangeHRM";
        Assert.assertEquals(actualTitle, expectedTitle);
        System.out.println(DriverManager.getDriver().getTitle());
    }
}
