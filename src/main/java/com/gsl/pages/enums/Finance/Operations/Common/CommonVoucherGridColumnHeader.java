package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

/**
 * Grid column headers shared identically (same header text) across the AR/AP/General Voucher
 * Sub-Ledger Information grids and the Adjustment Entry grid — a domain-specific
 * {@code *GridColumnHeader} enum (e.g. {@link com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader})
 * holds only the columns that differ per domain. Use {@link #resolveFor(VoucherDomain)} at call
 * sites so a future domain override or exclusion (see {@link VoucherDomain#overrideCommonColumn}/
 * {@link VoucherDomain#excludesCommonColumn}) needs no change here.
 */
public enum CommonVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    /** Sub-Ledger Information grid column — excluded for AR (see {@link VoucherDomain#AR}'s
     *  {@code excludesCommonColumn} override). */
    REFERENCE_SITE("Reference Site"),
    SUB_LEDGER_ID("Sub Ledger Id"),
    /** Adjustment Entry grid column — see {@code AdjustmentEntry}'s use of this header to scope
     *  its own row lookups. */
    ADJUSTED_AMOUNT("Adjusted Amount");

    private final String headerText;

    CommonVoucherGridColumnHeader(String headerText) { this.headerText = headerText; }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }

    /** Raw header text — use when the XPath needs the string directly, not as a column-id resolver. */
    public String headerText() { return headerText; }

    /**
     * Returns the domain-specific override if one exists, otherwise returns this.
     * Call sites use col.resolveFor(domain).columnIdXPath() — they never need to change
     * when a future domain overrides a specific column.
     */
    public IVoucherGridColumnHeader resolveFor(VoucherDomain domain) {
        return domain.overrideCommonColumn(this).orElse(this);
    }
}
