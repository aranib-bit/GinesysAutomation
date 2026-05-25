package com.gsl.pages.Finance.Operations;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.annotations.LogStep;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

import java.util.concurrent.TimeUnit;

public class ApVoucherListPage {

    private static final By APVOUCHERADD   = By.xpath("//span[text()='Add']");
    private static final By SEARCH_BOX     = By.xpath("//TODO-search-input");
    private static final By SEARCH_BUTTON  = By.xpath("//TODO-search-button");
    private static final By EDIT_ICON      = By.xpath("//TODO-edit-icon-first-row");
    private static final By DELETE_ICON    = By.xpath("//TODO-delete-icon-first-row");
    private static final By UNPOST_BUTTON  = By.xpath("//TODO-unpost-button");
    private static final By CONFIRM_DELETE = By.xpath("//TODO-confirm-delete-button");

    @LogStep("Open New AP Voucher Form")
    public ApVoucherAdd clickAdd() {
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        SeleniumUtils.click(APVOUCHERADD, WaitType.CLICKABLE, "APVOUCHERADD");
        return new ApVoucherAdd();
    }

    @LogStep("Search AP Voucher by Document Number")
    public ApVoucherListPage searchByVoucherNo(String voucherNo) {
        SeleniumUtils.sendKeys(SEARCH_BOX, WaitType.VISIBLE, voucherNo, "SEARCH_BOX");
        SeleniumUtils.click(SEARCH_BUTTON, WaitType.CLICKABLE, "SEARCH_BUTTON");
        Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
        return this;
    }

    @LogStep("Open AP Voucher for Edit")
    public ApVoucherAdd clickEditForVoucher() {
        SeleniumUtils.click(EDIT_ICON, WaitType.CLICKABLE, "EDIT_ICON");
        return new ApVoucherAdd();
    }

    @LogStep("Click Delete for AP Voucher")
    public ApVoucherListPage clickDeleteForVoucher() {
        SeleniumUtils.click(DELETE_ICON, WaitType.CLICKABLE, "DELETE_ICON");
        return this;
    }

    @LogStep("Unpost AP Voucher")
    public ApVoucherListPage clickUnpost() {
        SeleniumUtils.click(UNPOST_BUTTON, WaitType.CLICKABLE, "UNPOST_BUTTON");
        return this;
    }

    @LogStep("Confirm Delete Dialog")
    public ApVoucherListPage confirmDelete() {
        SeleniumUtils.click(CONFIRM_DELETE, WaitType.CLICKABLE, "CONFIRM_DELETE");
        return this;
    }
}
