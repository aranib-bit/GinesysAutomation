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

    private PageHeaderUtils() {}

    public static String getPageHeaderText() {
        return SeleniumUtils.getText(PAGE_HEADER_TEXT, "Page Header");
    }
}
