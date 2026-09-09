package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Literal, explicit Add/Edit form page-header text for each Finance voucher domain — e.g.
 * "Add: General Voucher" / "Edit: General Voucher" — as seen in the live pageheader toolbar once
 * a domain's Add/Edit form actually opens. Single source of truth for this text, mirroring
 * {@link FinanceVoucherListPageHeader}'s exact pattern for the LIST page's header:
 * {@code VoucherDomain.getAddFormHeader()}/{@code getEditFormHeader()} delegate to
 * {@link #addHeaderText()}/{@link #editHeaderText()} rather than computing or hardcoding the text
 * anywhere else (e.g. inline in a page object's own {@code verifyOnThisPage()}).
 *
 * <p><b>Only {@link #GV}'s text is confirmed live</b> — General Voucher's Add page is the only
 * domain whose Add/Edit page object implements {@code VerifiablePage} today (confirmed run
 * {@code 20260909_094016}, and via grep — {@code ArVoucherAdd}/{@code ApVoucherAdd} don't yet).
 * {@link #AR}/{@link #AP}/{@link #CONTRA} entries are reasonable-guess placeholders — confirm
 * against the live DOM before relying on them once those Add pages implement
 * {@code VerifiablePage} themselves (same "TODO-confirm" convention {@code VoucherDomain
 * #getMenuShortcode()} already uses for AR/AP, and {@code FinanceVoucherListPageHeader#CONTRA}'s
 * own "Placeholder" comment).
 */
public enum FinanceVoucherAddEditPageHeader {

    /** TODO-confirm live once AR's Add page implements VerifiablePage. */
    AR("Add: AR Voucher", "Edit: AR Voucher"),
    /** TODO-confirm live once AP's Add page implements VerifiablePage. */
    AP("Add: AP Voucher", "Edit: AP Voucher"),
    /** Confirmed live — screenshot evidence, run 20260909_094016. */
    GV("Add: General Voucher", "Edit: General Voucher"),
    /** Placeholder — Contra Voucher has no live Add page yet, mirrors
     *  {@code FinanceVoucherListPageHeader#CONTRA}. */
    CONTRA("Add: Contra Voucher", "Edit: Contra Voucher");

    private final String addHeaderText;
    private final String editHeaderText;

    FinanceVoucherAddEditPageHeader(String addHeaderText, String editHeaderText) {
        this.addHeaderText = addHeaderText;
        this.editHeaderText = editHeaderText;
    }

    public String addHeaderText() {
        return addHeaderText;
    }

    public String editHeaderText() {
        return editHeaderText;
    }
}
