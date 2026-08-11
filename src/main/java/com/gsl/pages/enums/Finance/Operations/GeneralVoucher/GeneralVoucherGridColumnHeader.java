package com.gsl.pages.enums.Finance.Operations.GeneralVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

public enum GeneralVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub Ledger");
    // General Voucher's Expense/Income Detail grid has no Reference Site column
    // (unlike AP/AR) — see GeneralVoucherExpenseIncomeDetail's row-index-based
    // cell-editor design, which never needs CommonVoucherGridColumnHeader.REFERENCE_SITE.

    private final String headerText;

    GeneralVoucherGridColumnHeader(String headerText) {
        this.headerText = headerText;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }
}
