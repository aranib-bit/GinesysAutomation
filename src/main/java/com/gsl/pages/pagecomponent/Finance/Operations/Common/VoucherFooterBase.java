package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Common save/close footer shared by AP, AR, and future voucher types.
 * Subclasses pass their VoucherDomain to the constructor; log messages and
 * the close-action key are derived automatically from the domain.
 */
public abstract class VoucherFooterBase {

    private static final By SAVEBUTTON   = By.xpath("//span[text()='ave']");
    private static final By CLOSE_BUTTON = By.xpath("//*[@class='mdi mdi-24px mdi-close']");

    private final String voucherLabel;
    private final String closeKey;

    protected VoucherFooterBase(VoucherDomain domain) {
        this.voucherLabel = domain.getVoucherLabel();
        this.closeKey     = domain.name() + "_VOUCHER_CLOSE";
    }

    public DocumentPopup clickSave() {
        ExtentLogger.info("Clicking Save on " + voucherLabel + " form...");
        SeleniumUtils.click(SAVEBUTTON, WaitType.CLICKABLE, "SAVEBUTTON");
        return new DocumentPopup();
    }

    /** Clicks the X to close the voucher form; returns the discard-confirmation dialog. */
    public DocumentMessageBox clickClose() {
        SeleniumUtils.click(CLOSE_BUTTON, WaitType.CLICKABLE, closeKey);
        ExtentLogger.info("i Confirm dialog detected after [" + closeKey + "] — handled by page flow");
        return new DocumentMessageBox();
    }
}
