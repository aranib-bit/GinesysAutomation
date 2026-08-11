package com.gsl.pages.Finance.Operations;

import com.gsl.annotations.LogStep;
import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.CommonButton;
import com.gsl.utils.PageHeaderUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * General Voucher list page — mirrors {@code ApVoucherListPage} exactly, including
 * its still-open {@code //TODO-} placeholder locators for search/edit/delete/unpost
 * (consistent, pre-existing debt across the codebase, not new here).
 */
public class GeneralVoucherListPage implements VerifiablePage {

    /** TODO: confirm against the live UI — best-effort guess, not yet verified. */
    private static final String EXPECTED_PAGE_HEADER = "General Vouchers";

    // GENERALVOUCHERADD → CommonButton.ADD.locator()
    private static final class Loc {
        static final By SEARCH_BOX     = By.xpath("//TODO-search-input");
        static final By SEARCH_BUTTON  = By.xpath("//TODO-search-button");
        static final By EDIT_ICON      = By.xpath("//TODO-edit-icon-first-row");
        static final By DELETE_ICON    = By.xpath("//TODO-delete-icon-first-row");
        static final By UNPOST_BUTTON  = By.xpath("//TODO-unpost-button");
        static final By CONFIRM_DELETE = By.xpath("//TODO-confirm-delete-button");
    }

    /**
     * Confirms the browser is actually showing the General Voucher list right now, via the
     * page-header text — same live-DOM-signal pattern as {@code ApVoucherListPage}.
     */
    @Override
    public void verifyOnThisPage() {
        String actual = PageHeaderUtils.getPageHeaderText();
        if (!EXPECTED_PAGE_HEADER.equalsIgnoreCase(actual)) {
            throw new IllegalStateException(
                    "Expected to be on the General Voucher list (page header '" + EXPECTED_PAGE_HEADER
                    + "') but the live page header reads '" + actual + "' instead.");
        }
    }

    @LogStep("Open New General Voucher Form")
    public GeneralVoucherAdd clickAdd() {
        SeleniumUtils.click(CommonButton.ADD.locator(), WaitType.CLICKABLE, "GENERALVOUCHERADD");
        return new GeneralVoucherAdd();
    }

    @LogStep("Search General Voucher by Document Number")
    public GeneralVoucherListPage searchByVoucherNo(String voucherNo) {
        SeleniumUtils.sendKeys(Loc.SEARCH_BOX, WaitType.VISIBLE, voucherNo, "SEARCH_BOX");
        SeleniumUtils.click(Loc.SEARCH_BUTTON, WaitType.CLICKABLE, "SEARCH_BUTTON");
        return this;
    }

    @LogStep("Open General Voucher for Edit")
    public GeneralVoucherAdd clickEditForVoucher() {
        SeleniumUtils.click(Loc.EDIT_ICON, WaitType.CLICKABLE, "EDIT_ICON");
        return new GeneralVoucherAdd();
    }

    @LogStep("Click Delete for General Voucher")
    public GeneralVoucherListPage clickDeleteForVoucher() {
        SeleniumUtils.click(Loc.DELETE_ICON, WaitType.CLICKABLE, "DELETE_ICON");
        return this;
    }

    @LogStep("Unpost General Voucher")
    public GeneralVoucherListPage clickUnpost() {
        SeleniumUtils.click(Loc.UNPOST_BUTTON, WaitType.CLICKABLE, "UNPOST_BUTTON");
        return this;
    }

    @LogStep("Confirm Delete Dialog")
    public GeneralVoucherListPage confirmDelete() {
        SeleniumUtils.click(Loc.CONFIRM_DELETE, WaitType.CLICKABLE, "CONFIRM_DELETE");
        return this;
    }
}
