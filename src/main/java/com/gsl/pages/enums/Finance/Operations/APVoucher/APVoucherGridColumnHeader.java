package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

public enum APVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub-Ledger"),
    DEDUCT_TDS("Deduct TDS");
    // REFERENCE_SITE and SUB_LEDGER_ID are identical across all domains — see CommonVoucherGridColumnHeader

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
