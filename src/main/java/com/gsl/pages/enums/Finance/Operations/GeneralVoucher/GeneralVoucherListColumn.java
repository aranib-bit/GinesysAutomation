package com.gsl.pages.enums.Finance.Operations.GeneralVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;

/**
 * List-grid columns specific to General Voucher — columns shared identically with AR/AP
 * live in {@link CommonVoucherListColumn}. Neither of these is mandatory (both appear in
 * GV's "Columns" chooser).
 */
public enum GeneralVoucherListColumn implements IVoucherListColumn {

    PAYMENT_AMT_CR("Payment Amt (Cr)", ColumnDataType.DECIMAL, false),
    RECEIPT_AMT_DR("Receipt Amt (Dr)", ColumnDataType.DECIMAL, false);

    private final String headerText;
    private final ColumnDataType dataType;
    private final boolean mandatory;

    GeneralVoucherListColumn(String headerText, ColumnDataType dataType, boolean mandatory) {
        this.headerText = headerText;
        this.dataType = dataType;
        this.mandatory = mandatory;
    }

    @Override
    public String headerText() {
        return headerText;
    }

    @Override
    public ColumnDataType dataType() {
        return dataType;
    }

    @Override
    public boolean mandatory() {
        return mandatory;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }
}
