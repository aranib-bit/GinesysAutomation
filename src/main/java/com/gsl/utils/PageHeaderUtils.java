package com.gsl.utils;

import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Reads the ExtJS page-header text (e.g. "AP Vouchers", "AR Vouchers") — a live, page-specific DOM
 * signal confirmed present on list pages via direct DevTools inspection. Unlike the module-level
 * URL check ({@code TopModuleMenuComponent.getActiveModuleName()}) or the submenu-highlight check
 * ({@code FinanceTopMenuComponent.getSelectedMenu()}), this identifies the SPECIFIC page currently
 * rendered, not just which module/submenu is active — the piece those two checks can't provide.
 */
public final class PageHeaderUtils {

    private static final By PAGE_HEADER_TEXT =
            By.xpath("//div[contains(@class,'pageheader-toolbar') and contains(@class,'x-toolbar-text')]");

    /**
     * The currently-open top-level ExtJS window/panel's own title-bar text (e.g. "Add: General
     * Voucher") — distinct from {@link #getPageHeaderText()}'s shared list-page toolbar text.
     *
     * <p>Confirmed live via DevTools that the Add/Edit form does NOT reuse the list page's
     * {@code pageheader-toolbar} component at all — it renders as its own window with a
     * {@code winTitleType1}-styled title (one level up from the {@code winTitleType2} nested-popup
     * title convention already established elsewhere in this codebase — see
     * {@code AdjustmentPopups#windowScopeXpath}/{@code ReferenceSiteWiseFIFOPopup}). Calling
     * {@link #getPageHeaderText()} against the Add/Edit form instead keeps matching the STALE
     * list-page header still sitting in the DOM underneath the form (confirmed live, repeatedly:
     * "the live page header reads 'General Vouchers' instead" while the Add form was genuinely,
     * correctly open on screen) — no amount of waiting/retrying fixes that, since it's reading
     * the wrong element entirely, not racing a real one.
     */
    private static final By WINDOW_TITLE_TEXT =
            By.xpath("//div[contains(@class,'winTitleType1')]");

    private PageHeaderUtils() {}

    public static String getPageHeaderText() {
        return SeleniumUtils.getText(PAGE_HEADER_TEXT, "Page Header");
    }

    /** See {@link #WINDOW_TITLE_TEXT}'s Javadoc for why this is a distinct method from
     *  {@link #getPageHeaderText()}, not a shared one. */
    public static String getWindowTitleText() {
        return SeleniumUtils.getText(WINDOW_TITLE_TEXT, "Window Title");
    }
}
