package com.gsl.pages.enums.Finance.Operations.ARVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

/**
 * Grid column headers specific to AR Voucher's Sub-Ledger Information grid — columns shared
 * identically across AR/AP/General Voucher (e.g. Reference Site, Sub Ledger Id) live in
 * {@code CommonVoucherGridColumnHeader} instead.
 */
public enum ARVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub Ledger");
    // REFERENCE_SITE and SUB_LEDGER_ID are identical across all domains — see CommonVoucherGridColumnHeader

    private final String headerText;

    ARVoucherGridColumnHeader(String headerText) {
        this.headerText = headerText;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }

    @Override
    public String headerText() { return headerText; }
}
