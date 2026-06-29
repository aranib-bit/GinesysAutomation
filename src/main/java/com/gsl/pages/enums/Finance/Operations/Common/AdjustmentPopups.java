package com.gsl.pages.enums.Finance.Operations.Common;

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
 *             AdjustmentPopups.REFERENCE_SITEWISE_FIFO_SETOFF.popupWindowLocator()));
 * </pre>
 *
 * Notes:
 * - {@code FIFO_SETOFF} applies its action inline within the existing Adjustment Entry,
 *   so it has no popup window — its {@code popupTitle} is {@code null} and
 *   {@link #popupWindowLocator()} / {@link #windowScopeXpath()} will throw if called.
 * - {@code SELECT_DOCUMENT} does open a popup, but no code currently scopes it by
 *   {@code winTitleType2}. Its {@code popupTitle} stays {@code null} until verified
 *   against the live UI.
 */
public enum AdjustmentPopups {

    SELECT_DOCUMENT                 ("Select Document",                    null),
    FIFO_SETOFF                     ("FIFO Set-Off",                       null),
    REFERENCE_SITEWISE_FIFO_SETOFF  ("Reference Site wise FIFO set-off",
                                     "Reference Site wise FIFO set-off");

    private final String menuOption;
    private final String popupTitle;

    AdjustmentPopups(String menuOption, String popupTitle) {
        this.menuOption = menuOption;
        this.popupTitle = popupTitle;
    }

    public String menuOption() { return menuOption; }
    public String popupTitle() { return popupTitle; }

    /** Toolbar button inside the Adjusted Document dropdown. */
    public By menuOptionLocator() {
        return By.xpath("//span[text()='" + menuOption
                + "' and @class='x-btn-inner x-btn-inner-default-toolbar-small']");
    }

    /**
     * Window-scope xpath for the ExtJS dialog opened by this option, scoped by the
     * {@code winTitleType2} header text. Only valid for constants that own a
     * {@code popupTitle}; throws otherwise so the caller learns at the call site.
     */
    public String windowScopeXpath() {
        if (popupTitle == null) {
            throw new IllegalStateException(
                    "AdjustmentPopups." + name() + " has no popupTitle configured");
        }
        return "//div[contains(@class,'x-window') and @role='dialog' and "
             + ".//div[contains(@class,'winTitleType2') and normalize-space()='"
             + popupTitle + "']]";
    }

    /** {@link By} form of {@link #windowScopeXpath()} for use with explicit waits. */
    public By popupWindowLocator() {
        return By.xpath(windowScopeXpath());
    }
}
