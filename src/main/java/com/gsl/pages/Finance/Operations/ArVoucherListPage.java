package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.CommonButton;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ArVoucherListPage implements VerifiablePage {

    private static final String EXPECTED_PAGE_HEADER = "AR Vouchers";

    // ARVOUCHERADD → CommonButton.ADD.locator()
    // TODO: replace placeholder XPaths below with actual selectors from the live AR Voucher list UI
    private static final class Loc {
        static final By SEARCH_BOX     = By.xpath("//TODO-search-input");
        static final By SEARCH_BUTTON  = By.xpath("//TODO-search-button");
        static final By EDIT_ICON      = By.xpath("//TODO-edit-icon-first-row");
        static final By DELETE_ICON    = By.xpath("//TODO-delete-icon-first-row");
        static final By UNPOST_BUTTON  = By.xpath("//TODO-unpost-button");
        static final By CONFIRM_DELETE = By.xpath("//TODO-confirm-delete-button");
    }

    /**
     * Confirms the browser is actually showing the AR Voucher list right now, via the page-header
     * text — a live signal independent of URL/menu-highlight state. Called by {@code NavigationUtils}
     * right after navigation lands, before a caller trusts this page object (e.g. before clicking
     * the generic, page-agnostic "Add" button that exists on most list pages).
     */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        if (!EXPECTED_PAGE_HEADER.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Expected to be on the AR Voucher list (page header '" + EXPECTED_PAGE_HEADER
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    @LogStep("Open New AR Voucher Form")
    public ArVoucherAdd clickAdd(){
        SeleniumUtils.click(CommonButton.ADD.locator(), WaitType.CLICKABLE, "ARVOUCHERADD");
        return new ArVoucherAdd();
    }

    /**
     * Types the document number into the search box and clicks the search button.
     * Waits briefly for the result list to refresh.
     *
     * @param voucherNo the {@code scheme_docno} to search for
     * @return {@code this} for fluent chaining
     */
    @LogStep("Search AR Voucher by Document Number")
    public ArVoucherListPage searchByVoucherNo(String voucherNo) {
        SeleniumUtils.sendKeys(Loc.SEARCH_BOX, WaitType.VISIBLE, voucherNo, "SEARCH_BOX");
        SeleniumUtils.click(Loc.SEARCH_BUTTON, WaitType.CLICKABLE, "SEARCH_BUTTON");
        return this;
    }

    /**
     * Clicks the Edit icon on the first (expected: only) row after a search.
     * In GSL Finance the Edit form reuses the same Add page — returns {@link ArVoucherAdd}.
     *
     * @return the AR Voucher form in edit mode
     */
    @LogStep("Open AR Voucher for Edit")
    public ArVoucherAdd clickEditForVoucher() {
        SeleniumUtils.click(Loc.EDIT_ICON, WaitType.CLICKABLE, "EDIT_ICON");
        return new ArVoucherAdd();
    }

    /**
     * Clicks the Delete icon on the first (expected: only) row after a search.
     * Does NOT confirm — call {@link #confirmDelete()} separately.
     *
     * @return {@code this} for fluent chaining
     */
    @LogStep("Click Delete for AR Voucher")
    public ArVoucherListPage clickDeleteForVoucher() {
        SeleniumUtils.click(Loc.DELETE_ICON, WaitType.CLICKABLE, "DELETE_ICON");
        return this;
    }

    /**
     * Clicks the Unpost button for a posted ({@code release_status='P'}) voucher.
     * Call this before editing or deleting a posted voucher.
     *
     * @return {@code this} for fluent chaining
     */
    @LogStep("Unpost AR Voucher")
    public ArVoucherListPage clickUnpost() {
        SeleniumUtils.click(Loc.UNPOST_BUTTON, WaitType.CLICKABLE, "UNPOST_BUTTON");
        return this;
    }

    /**
     * Confirms the delete confirmation dialog (OK / Yes button).
     *
     * @return {@code this} for fluent chaining
     */
    @LogStep("Confirm Delete Dialog")
    public ArVoucherListPage confirmDelete() {
        SeleniumUtils.click(Loc.CONFIRM_DELETE, WaitType.CLICKABLE, "CONFIRM_DELETE");
        return this;
    }
}
