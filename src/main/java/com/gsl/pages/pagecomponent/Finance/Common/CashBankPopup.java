package com.gsl.pages.pagecomponent.Finance.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanGeneral;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
        SeleniumUtils.sendKeys(Loc.CASHBANKSEARCHBOX, cashbankname, "CASHBANKSEARCHBOX");

        By selectionBy = getCashBankSelection(cashbankname);
        WebDriverWait wait = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().popupWaitSeconds());
        JavascriptExecutorUtils.waitForExtJS();

        // Wait for the grid row to appear and be visible after ExtJS search reload
        wait.until(ExpectedConditions.visibilityOfElementLocated(selectionBy));

        // clickWithStaleRetry composes stale-element retry with clickWithOverlayRetry's
        // mask/backdrop-aware retry — covers both the grid re-rendering between find and click
        // (StaleElementReferenceException) AND the ExtJS loadmask still covering the row when the
        // click fires (ElementClickInterceptedException), which the previous hand-rolled loop
        // (stale-only catch, raw driver.findElement(...).click()) did not guard against.
        //
        // Round 14 (§26): also confirm the row is actually SELECTED in the grid's selection model
        // before proceeding to OK — this popup drives the IDENTICAL widget (byte-identical row
        // locator) as AllocateSitesPopup.searchSite, whose measured root cause applies here too:
        // the picker can open showing this row from the unfiltered list before the async
        // search-reload lands, so a click satisfied against that pre-reload row can be silently
        // discarded. Re-click (fresh-resolved) until selection is confirmed, rather than clicking
        // once and hoping — see SeleniumUtils.isRowSelected's Javadoc for the full mechanism.
        SeleniumUtils.actAndConfirmStable(
                () -> SeleniumUtils.clickWithStaleRetry(selectionBy, WaitType.CLICKABLE, "CASHBANK_ROW_" + cashbankname),
                () -> SeleniumUtils.isRowSelected(selectionBy),
                150L, 3, "CashBank_RowSelected_" + cashbankname);
        ExtentLogger.info("[CASHBANK SELECT] Selected | CashBank=" + cashbankname);

        SeleniumUtils.click(Loc.CASHBANKPOPUPOKBUTTON, WaitType.CLICKABLE, "OK");
        return parentPage;
    }

    public CashBankParent cancel() {
        SeleniumUtils.click(Loc.CASHBANKPOPUPCANCELBUTTON, WaitType.CLICKABLE, "Cancel");
        return parentPage;
    }





}
