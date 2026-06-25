package com.gsl.pages.pagecomponent.Finance.Common;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanGeneral;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class CashBankPopup {

    private static final class Loc {
        static final By CASHBANKSEARCHBOX = By.xpath("//span[normalize-space()='Search:']/ancestor::label/following-sibling::div//input[contains(@id,'textfield') and contains(@id,'-inputEl')]");
        static final String CASHBANKSELECTION = "//div[contains(@class,'x-grid-item-container')]//tr//td[count(//div[@role='columnheader'][.//span[text()='Name']]/preceding-sibling::div)+1]//div[normalize-space()='%s']";
        // ExtJS truncated-text workarounds — keep as-is until UI rendering changes
        static final By CASHBANKPOPUPOKBUTTON     = By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'K']");
        static final By CASHBANKPOPUPCANCELBUTTON = By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'ancel']");
    }

    private final CashBankParent parentPage;

    public CashBankPopup(CashBankParent parentPage) {
        this.parentPage = parentPage;
    }

    public CashBankPopup sendCashBankSearch(String cashbank){
        SeleniumUtils.sendKeys(Loc.CASHBANKSEARCHBOX, cashbank, "CASHBANKSEARCHBOX");
        return this;
    }

    private By getCashBankSelection(String cashbank) {
        String xpath = String.format(Loc.CASHBANKSELECTION, cashbank);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    public CashBankParent selectCashBank(String cashbankname) {
        WebDriver driver = DriverManager.getDriver();
        SeleniumUtils.sendKeys(Loc.CASHBANKSEARCHBOX, cashbankname, "CASHBANKSEARCHBOX");

        By selectionBy = getCashBankSelection(cashbankname);
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().popupWaitSeconds());
        JavascriptExecutorUtils.waitForExtJS();

        // Wait for the grid row to appear and be visible after ExtJS search reload
        wait.until(ExpectedConditions.visibilityOfElementLocated(selectionBy));

        // Retry click in case ExtJS re-renders the grid between find and click
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                driver.findElement(selectionBy).click();
                if (attempt > 0) {
                    ExtentLogger.info("[CASHBANK SELECT] Retry succeeded on attempt " + (attempt + 1) + " | CashBank=" + cashbankname);
                } else {
                    ExtentLogger.info("[CASHBANK SELECT] Selected on first attempt | CashBank=" + cashbankname);
                }
                break;
            } catch (StaleElementReferenceException e) {
                ExtentLogger.info("[CASHBANK SELECT] StaleElementReferenceException on attempt " + (attempt + 1) + " | CashBank=" + cashbankname + " | Retrying...");
                System.out.println("StaleElementReferenceException on cashbank click, attempt " + (attempt + 1));
                if (attempt == 2) {
                    ExtentLogger.info("[CASHBANK SELECT] All 3 attempts failed with StaleElementReferenceException | CashBank=" + cashbankname);
                    throw e;
                }
                Uninterruptibles.sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
            }
        }

        SeleniumUtils.click(Loc.CASHBANKPOPUPOKBUTTON, WaitType.CLICKABLE, "OK");
        return parentPage;
    }

    public CashBankParent cancel() {
        SeleniumUtils.click(Loc.CASHBANKPOPUPCANCELBUTTON, WaitType.CLICKABLE, "Cancel");
        return parentPage;
    }





}
