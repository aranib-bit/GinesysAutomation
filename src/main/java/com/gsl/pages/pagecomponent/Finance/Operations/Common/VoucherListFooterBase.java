package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.annotations.LogStep;
import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Common list-page pagination-footer region shared by AR, AP, GV (and future Contra)
 * voucher list pages — the first/prev/page-number/next/last/refresh controls and the
 * "Displaying X-Y of Z" text, as seen in the bottom region of the list page screenshot.
 *
 * <p>Locators are exposed as {@code protected} methods rather than {@code private static final}
 * fields so a domain subclass can override just one of them if that list page's DOM ever
 * diverges, without having to fork this whole class.
 */
public abstract class VoucherListFooterBase {

    /** Short existence-check timeout for enabled/disabled state probes — same idiom used
     *  elsewhere in the codebase (e.g. {@code AccountGroupListPage}) for state checks. */
    private static final int STATE_CHECK_TIMEOUT_SECONDS = 2;

    private final VoucherDomain domain;

    protected VoucherListFooterBase(VoucherDomain domain) {
        this.domain = domain;
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By refreshLocator() {
        return By.xpath("//span[@data-ref='btnIconEl' and contains(@class,'x-tbar-loading')]");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By nextPageEnabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-next')]/ancestor::a[1][@aria-disabled='false']");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By nextPageDisabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-next')]/ancestor::a[1][@aria-disabled='true']");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By lastPageEnabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-last')]/ancestor::a[1][@aria-disabled='false']");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By lastPageDisabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-last')]/ancestor::a[1][@aria-disabled='true']");
    }

    /** TODO: inferred, not explicitly confirmed — follows the same {@code aria-disabled} +
     *  ExtJS-standard toolbar class-name pattern confirmed for Next/Last. */
    protected By firstPageEnabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-first')]/ancestor::a[1][@aria-disabled='false']");
    }

    /** TODO: inferred, not explicitly confirmed — see {@link #firstPageEnabledLocator()}. */
    protected By firstPageDisabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-first')]/ancestor::a[1][@aria-disabled='true']");
    }

    /** TODO: inferred, not explicitly confirmed — see {@link #firstPageEnabledLocator()}. */
    protected By previousPageEnabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-prev')]/ancestor::a[1][@aria-disabled='false']");
    }

    /** TODO: inferred, not explicitly confirmed — see {@link #firstPageEnabledLocator()}. */
    protected By previousPageDisabledLocator() {
        return By.xpath("//span[contains(@class,'x-tbar-page-prev')]/ancestor::a[1][@aria-disabled='true']");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By currentPageInputLocator() {
        return By.xpath("//input[@data-ref='inputEl' and @role='spinbutton']");
    }

    /** Confirmed against the live DOM (General Voucher list) — the "of N" text label. */
    protected By totalPageTextLocator() {
        return By.xpath("//div[contains(@class,'x-toolbar-text') and starts-with(normalize-space(),'of ')]");
    }

    /** Confirmed against the live DOM (General Voucher list). */
    protected By displayingTextLocator() {
        return By.xpath("//div[contains(@class,'x-toolbar-text') and starts-with(normalize-space(),'Displaying ')]");
    }

    /** Clicks the Refresh (loading) icon in the pagination footer. */
    @LogStep("Click Refresh")
    public void clickRefresh() {
        SeleniumUtils.click(refreshLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_FOOTER_REFRESH");
    }

    /** Clicks the Next Page control. Only resolves when it's enabled — a disabled Next Page
     *  means there's no next page to go to, in which case this throws a not-found timeout. */
    @LogStep("Click Next Page")
    public void clickNextPage() {
        SeleniumUtils.click(nextPageEnabledLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_FOOTER_NEXT_PAGE");
    }

    /** {@code true} if the Next Page control is currently enabled. */
    public boolean isNextPageEnabled() {
        return SeleniumUtils.isElementPresent(nextPageEnabledLocator(), STATE_CHECK_TIMEOUT_SECONDS);
    }

    /** Clicks the Last Page control. Only resolves when it's enabled. */
    @LogStep("Click Last Page")
    public void clickLastPage() {
        SeleniumUtils.click(lastPageEnabledLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_FOOTER_LAST_PAGE");
    }

    /** {@code true} if the Last Page control is currently enabled. */
    public boolean isLastPageEnabled() {
        return SeleniumUtils.isElementPresent(lastPageEnabledLocator(), STATE_CHECK_TIMEOUT_SECONDS);
    }

    /** Clicks the First Page control. Only resolves when it's enabled. */
    @LogStep("Click First Page")
    public void clickFirstPage() {
        SeleniumUtils.click(firstPageEnabledLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_FOOTER_FIRST_PAGE");
    }

    /** {@code true} if the First Page control is currently enabled. */
    public boolean isFirstPageEnabled() {
        return SeleniumUtils.isElementPresent(firstPageEnabledLocator(), STATE_CHECK_TIMEOUT_SECONDS);
    }

    /** Clicks the Previous Page control. Only resolves when it's enabled. */
    @LogStep("Click Previous Page")
    public void clickPreviousPage() {
        SeleniumUtils.click(previousPageEnabledLocator(), WaitType.CLICKABLE, domain.name() + "_VOUCHER_FOOTER_PREVIOUS_PAGE");
    }

    /** {@code true} if the Previous Page control is currently enabled. */
    public boolean isPreviousPageEnabled() {
        return SeleniumUtils.isElementPresent(previousPageEnabledLocator(), STATE_CHECK_TIMEOUT_SECONDS);
    }

    /** Reads the current page number from the pagination-toolbar spinbutton input. */
    public int getCurrentPageNo() {
        String value = SeleniumUtils.getAttribute(currentPageInputLocator(), "value", domain.name() + "_VOUCHER_FOOTER_CURRENT_PAGE_NO");
        return Integer.parseInt(value.trim());
    }

    /** Reads the total page count from the "of N" text label next to the page-number input. */
    public int getTotalPageNo() {
        String text = SeleniumUtils.getText(totalPageTextLocator(), domain.name() + "_VOUCHER_FOOTER_TOTAL_PAGE_NO");
        return Integer.parseInt(text.replaceAll("[^0-9]", "").trim());
    }

    /** Reads the "Displaying X-Y of Z" footer text. */
    public String getDisplayingText() {
        return SeleniumUtils.getText(displayingTextLocator(), domain.name() + "_VOUCHER_FOOTER_DISPLAYING_TEXT");
    }
}
