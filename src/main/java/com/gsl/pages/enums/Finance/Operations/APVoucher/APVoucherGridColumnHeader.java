package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

public enum APVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub-Ledger"),
    REFERENCE_SITE("Reference Site"),
    SUB_LEDGER_ID("Sub Ledger Id");

    private final String headerText;

    APVoucherGridColumnHeader(String headerText) {
        this.headerText = headerText;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }
}
