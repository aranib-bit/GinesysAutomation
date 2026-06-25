package com.gsl.pages.pagecomponent.Finance.Common;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TdsNamePopup {

    private static final By SEARCH_BOX = By.xpath(
            "//span[normalize-space()='Search:']/ancestor::label" +
            "/following-sibling::div//input[contains(@id,'textfield') and contains(@id,'-inputEl')]");

    private static final String SELECTION_TEMPLATE =
            "//div[contains(@class,'x-grid-item-container')]//tr" +
            "//td[count(//div[@role='columnheader'][.//span[text()='Name']]" +
            "/preceding-sibling::div)+1]//div[normalize-space()='%s']";

    private static final By OK_BUTTON = By.xpath(
            "//div[contains(@class,'x-title-text') and normalize-space()='TDS Name']/ancestor::div[contains(@class,'x-window')]     //a[@role='button' and .//span[normalize-space()='OK']]");

    private static final By CANCEL_BUTTON = By.xpath(
            "//div[contains(@class,'x-title-text') and normalize-space()='TDS Name']/ancestor::div[contains(@class,'x-window')]     //a[@role='button' and .//span[normalize-space()='Cancel']]");

    private final TdsNameParent parentPage;

    public TdsNamePopup(TdsNameParent parentPage) {
        this.parentPage = parentPage;
    }

    public TdsNameParent selectTdsName(String tdsName) {
        SeleniumUtils.sendKeys(SEARCH_BOX, tdsName, "TDS_NAME_SEARCH");
        JavascriptExecutorUtils.waitForExtJS();

        By row = By.xpath(String.format(SELECTION_TEMPLATE, tdsName));
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().popupWaitSeconds());
        wait.until(ExpectedConditions.visibilityOfElementLocated(row));

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                driver.findElement(row).click();
                if (attempt > 0) {
                    ExtentLogger.info("[TDS NAME SELECT] Retry succeeded on attempt " + (attempt + 1) + " | TdsName=" + tdsName);
                } else {
                    ExtentLogger.info("[TDS NAME SELECT] Selected on first attempt | TdsName=" + tdsName);
                }
                break;
            } catch (StaleElementReferenceException e) {
                ExtentLogger.info("[TDS NAME SELECT] StaleElementReferenceException on attempt " + (attempt + 1) + " | TdsName=" + tdsName + " | Retrying...");
                if (attempt == 2) throw e;
                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            }
        }

        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "TDS_NAME_POPUP_OK");
        return parentPage;
    }

    public TdsNameParent cancel() {
        SeleniumUtils.click(CANCEL_BUTTON, WaitType.CLICKABLE, "TDS_NAME_POPUP_CANCEL");
        return parentPage;
    }
}
