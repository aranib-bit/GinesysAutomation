package com.gsl.pages.enums.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

public enum CommonVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    REFERENCE_SITE("Reference Site"),
    SUB_LEDGER_ID("Sub Ledger Id"),
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
