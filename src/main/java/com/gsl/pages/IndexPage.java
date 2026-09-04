package com.gsl.pages;

import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;

import static com.gsl.utils.Selenium.SeleniumUtils.sendKeys;

public class IndexPage {

    private static final By GINESYS_LOADER=By.id("splashScreenProgressInner");
    private static final By GINESYS_LOADER_DETAILS=By.id("splashScreenLoading");
    private static final By GINESYS_SPLASHSCREEN=By.id("splashScreen");


    public IndexPage checksplashScreenstatus() {
        // Round 14 (§27c): was a bare, single-attempt 30s elementDisappear — now the same
        // staged, logged wait waitForClickOverlay() already uses for this exact element.
        WaitUtils.waitForSplashScreenClear();
        return this;
    }

}
