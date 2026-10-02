package com.gsl.pages.pagecomponent.Finance.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan.GoodsReceiveChallanGeneral;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Drives the "Cash/Bank" search-and-select popup grid, shared by AR/AP/GV voucher Add/Edit forms
 * (opened via {@link com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase}).
 * Search text is typed into a filter box, the ExtJS grid reloads asynchronously, and the matching
 * row is clicked and re-confirmed as selected (see {@link #selectCashBank(String)}) before the
 * popup's OK button is clicked.
 *
 * <p>Constructed with the {@link CashBankParent} page that owns it, so {@link
 * #selectCashBank(String)}/{@link #cancel()} can hand back a correctly-typed reference to that
 * page once the popup closes.
 */
public class CashBankPopup {

    private static final class Loc {
        static final By CASHBANKSEARCHBOX = By.xpath("//span[normalize-space()='Search:']/ancestor::label/following-sibling::div//input[contains(@id,'textfield') and contains(@id,'-inputEl')]");
        static final String CASHBANKSELECTION = "//div[contains(@class,'x-grid-item-container')]//tr//td[count(//div[@role='columnheader'][.//span[text()='Name']]/preceding-sibling::div)+1]//div[normalize-space()='%s']";
        // ExtJS truncated-text workarounds — keep as-is until UI rendering changes
        static final By CASHBANKPOPUPOKBUTTON     = By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'K']");
        static final By CASHBANKPOPUPCANCELBUTTON = By.xpath("//span[contains(@id, 'button') and contains(@id, 'btnInnerEl') and text() = 'ancel']");
    }

    private final CashBankParent parentPage;

    /** @param parentPage the page that opened this popup; returned by {@link #selectCashBank(String)}/{@link #cancel()}. */
    public CashBankPopup(CashBankParent parentPage) {
        this.parentPage = parentPage;
    }

    /** Types {@code cashbank} into the popup's search box, without waiting for or selecting a row. */
    public CashBankPopup sendCashBankSearch(String cashbank){
        SeleniumUtils.sendKeys(Loc.CASHBANKSEARCHBOX, cashbank, "CASHBANKSEARCHBOX");
        return this;
    }

    /** Builds the locator for {@code cashbank}'s grid row, keyed off the "Name" column. */
    private By getCashBankSelection(String cashbank) {
        String xpath = String.format(Loc.CASHBANKSELECTION, cashbank);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    /**
     * Types {@code cashbankname} into the search box, waits for its matching grid row to render
     * after the ExtJS search-reload, clicks it (retrying through stale-element/overlay races and
     * re-confirming the row is actually selected in the grid's selection model — see {@link
     * SeleniumUtils#isRowSelected}), then clicks OK.
     *
     * @return the parent page passed into this popup's constructor
     */
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
        ReportLogger.fieldEntry("Cash/Bank selected: '" + cashbankname + "'");

        SeleniumUtils.click(Loc.CASHBANKPOPUPOKBUTTON, WaitType.CLICKABLE, "OK");
        return parentPage;
    }

    /** Dismisses the popup without selecting anything and returns the parent page. */
    public CashBankParent cancel() {
        SeleniumUtils.click(Loc.CASHBANKPOPUPCANCELBUTTON, WaitType.CLICKABLE, "Cancel");
        return parentPage;
    }





}
