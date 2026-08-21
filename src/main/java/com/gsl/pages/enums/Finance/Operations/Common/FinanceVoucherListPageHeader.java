package com.gsl.pages.enums.Finance.Operations.Common;

import org.openqa.selenium.By;

/**
 * Literal, explicit list-page header text for each Finance voucher list page — e.g. "AR
 * Vouchers", "AP Vouchers", "General Vouchers" — as seen in the live pageheader toolbar
 * (confirmed against the live DOM via DevTools on the AR/General Voucher list pages).
 *
 * <p>This is the single source of truth for that text: {@code VoucherDomain.getListPageHeader()}
 * delegates to {@link #headerText()} rather than computing it, and any header-toolbar control
 * that needs to be scoped to its own list page's toolbar (so it can't collide with a
 * same-labelled control elsewhere on a busy ExtJS page) should go through
 * {@link #scopedToolbarButtonLocator(String)}.
 */
public enum FinanceVoucherListPageHeader {

    AR("AR Vouchers"),
    AP("AP Vouchers"),
    GV("General Vouchers"),
    /** Placeholder — Contra Voucher has no live list page yet, mirrors {@code ContraVoucherListColumn}. */
    CONTRA("Contra Vouchers");

    private final String headerText;

    FinanceVoucherListPageHeader(String headerText) {
        this.headerText = headerText;
    }

    public String headerText() {
        return headerText;
    }

    /**
     * Scopes a header-toolbar button (e.g. "Add") to THIS list page's own toolbar, so it can't
     * collide with a same-labelled button elsewhere on the page.
     *
     * <p>Confirmed against the live DOM (AR Voucher list) — e.g. for {@code buttonLabel="Add"}:
     * {@code //div[normalize-space()='AR Vouchers']/ancestor::div[@role='toolbar'][1]//span[@data-ref='btnInnerEl' and normalize-space()='Add']}
     *
     * @param buttonLabel the visible button text, e.g. {@code "Add"}
     */
    public By scopedToolbarButtonLocator(String buttonLabel) {
        return By.xpath("//div[normalize-space()='" + headerText
                + "']/ancestor::div[@role='toolbar'][1]//span[@data-ref='btnInnerEl' and normalize-space()='"
                + buttonLabel + "']");
    }
}
