package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.CommonButton;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ApVoucherListPage implements VerifiablePage {

    private static final String EXPECTED_PAGE_HEADER = "AP Vouchers";

    // APVOUCHERADD → CommonButton.ADD.locator()
    // TODO: replace placeholder XPaths below with actual selectors from the live AP Voucher list UI
    private static final class Loc {
        static final By SEARCH_BOX     = By.xpath("//TODO-search-input");
        static final By SEARCH_BUTTON  = By.xpath("//TODO-search-button");
        static final By EDIT_ICON      = By.xpath("//TODO-edit-icon-first-row");
        static final By DELETE_ICON    = By.xpath("//TODO-delete-icon-first-row");
        static final By UNPOST_BUTTON  = By.xpath("//TODO-unpost-button");
        static final By CONFIRM_DELETE = By.xpath("//TODO-confirm-delete-button");
    }

    /**
     * Confirms the browser is actually showing the AP Voucher list right now, via the page-header
     * text — a live signal independent of URL/menu-highlight state. Called by {@code NavigationUtils}
     * right after navigation lands, before a caller trusts this page object (e.g. before clicking
     * the generic, page-agnostic "Add" button that exists on most list pages).
     */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        if (!EXPECTED_PAGE_HEADER.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Expected to be on the AP Voucher list (page header '" + EXPECTED_PAGE_HEADER
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    @LogStep("Open New AP Voucher Form")
    public ApVoucherAdd clickAdd() {
        SeleniumUtils.click(CommonButton.ADD.locator(), WaitType.CLICKABLE, "APVOUCHERADD");
        return new ApVoucherAdd();
    }

    @LogStep("Search AP Voucher by Document Number")
    public ApVoucherListPage searchByVoucherNo(String voucherNo) {
        SeleniumUtils.sendKeys(Loc.SEARCH_BOX, WaitType.VISIBLE, voucherNo, "SEARCH_BOX");
        SeleniumUtils.click(Loc.SEARCH_BUTTON, WaitType.CLICKABLE, "SEARCH_BUTTON");
        return this;
    }

    @LogStep("Open AP Voucher for Edit")
    public ApVoucherAdd clickEditForVoucher() {
        SeleniumUtils.click(Loc.EDIT_ICON, WaitType.CLICKABLE, "EDIT_ICON");
        return new ApVoucherAdd();
    }

    @LogStep("Click Delete for AP Voucher")
    public ApVoucherListPage clickDeleteForVoucher() {
        SeleniumUtils.click(Loc.DELETE_ICON, WaitType.CLICKABLE, "DELETE_ICON");
        return this;
    }

    @LogStep("Unpost AP Voucher")
    public ApVoucherListPage clickUnpost() {
        SeleniumUtils.click(Loc.UNPOST_BUTTON, WaitType.CLICKABLE, "UNPOST_BUTTON");
        return this;
    }

    @LogStep("Confirm Delete Dialog")
    public ApVoucherListPage confirmDelete() {
        SeleniumUtils.click(Loc.CONFIRM_DELETE, WaitType.CLICKABLE, "CONFIRM_DELETE");
        return this;
    }
}
