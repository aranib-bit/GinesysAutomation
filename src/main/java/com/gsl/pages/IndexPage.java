package com.gsl.pages;

import com.gsl.reports.ExtentLogger;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.elementDisappear;
import static com.gsl.utils.Selenium.SeleniumUtils.sendKeys;

public class IndexPage {

    private static final By GINESYS_LOADER=By.id("splashScreenProgressInner");
    private static final By GINESYS_LOADER_DETAILS=By.id("splashScreenLoading");
    private static final By GINESYS_SPLASHSCREEN=By.id("splashScreen");


    public IndexPage checksplashScreenstatus() {
        elementDisappear(GINESYS_SPLASHSCREEN,"SplashScreen");
        return this;
    }

}
