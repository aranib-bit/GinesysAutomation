package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import org.openqa.selenium.By;

/**
 * Centralised registry for the Adjusted Document menu options on Adjustment Entry,
 * plus the window title of the popup each option opens (when one opens).
 *
 * Usage — call locator() / popupWindowLocator() at the use-site, never store
 * the result in a field:
 * <pre>
 *     SeleniumUtils.click(AdjustmentPopups.SELECT_DOCUMENT.menuOptionLocator(),
 *                         WaitType.CLICKABLE, "Select Document Option");
 *
 *     WaitUtils.webDriverWait(...).until(ExpectedConditions.visibilityOfElementLocated(
 *             AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.popupWindowLocator(VoucherDomain.AP)));
 * </pre>
 *
 * Notes:
 * - {@code FIFO_SETOFF} applies its action inline within the existing Adjustment Entry,
 *   so it has no popup window — its popup titles are {@code null} and
 *   {@link #popupWindowLocator(VoucherDomain)} / {@link #windowScopeXpath(VoucherDomain)}
 *   will throw if called.
 * - {@code SELECT_DOCUMENT} does open a popup, but no code currently scopes it by
 *   {@code winTitleType2}. Its popup titles stay {@code null} until verified against the
 *   live UI.
 * - {@code REFERENCE_SITEWISE_FIFO_SETOFF} renders different {@code winTitleType2} text
 *   depending on voucher domain — AR Voucher shows "Reference Site wise FIFO set-off",
 *   AP Voucher shows the abbreviated "Ref. Site-wise FIFO Set-Off" — so its window-scope
 *   xpath is resolved per {@link VoucherDomain}.
 */
public enum AdjustmentPopups {

    SELECT_DOCUMENT                 ("Select Document",                    null, null),
    FIFO_SETOFF                     ("FIFO Set-Off",                       null, null),
    REFERENCE_SITEWISE_FIFO_SETOFF  ("Reference Site wise FIFO set-off",
                                     "Reference Site wise FIFO set-off",   // AR Voucher title
                                     "Ref. Site-wise FIFO Set-Off");        // AP Voucher title

    private final String menuOption;
    private final String popupTitleAr;
    private final String popupTitleAp;

    AdjustmentPopups(String menuOption, String popupTitleAr, String popupTitleAp) {
        this.menuOption = menuOption;
        this.popupTitleAr = popupTitleAr;
        this.popupTitleAp = popupTitleAp;
    }

    public String menuOption() { return menuOption; }

    /** Toolbar button inside the Adjusted Document dropdown. */
    public By menuOptionLocator() {
        return By.xpath("//span[text()='" + menuOption
                + "' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");
    }

    /**
     * Window-scope xpath for the ExtJS dialog opened by this option, scoped by the
     * {@code winTitleType2} header text for the given voucher domain. Only valid for
     * constants that own a popup title for that domain; throws otherwise so the caller
     * learns at the call site.
     */
    public String windowScopeXpath(VoucherDomain domain) {
        String popupTitle = domain == VoucherDomain.AP ? popupTitleAp : popupTitleAr;
        if (popupTitle == null) {
            throw new IllegalStateException(
                    "AdjustmentPopups." + name() + " has no popupTitle configured for " + domain);
        }
        return "//div[contains(@class,'x-window') and @role='dialog' and "
             + ".//div[contains(@class,'winTitleType2') and normalize-space()='"
             + popupTitle + "']]";
    }

    /** {@link By} form of {@link #windowScopeXpath(VoucherDomain)} for use with explicit waits. */
    public By popupWindowLocator(VoucherDomain domain) {
        return By.xpath(windowScopeXpath(domain));
    }
}
