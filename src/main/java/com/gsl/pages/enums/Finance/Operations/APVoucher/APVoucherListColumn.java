package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;

/**
 * List-grid columns specific to AP Voucher — columns shared identically with AR/GV live
 * in {@link CommonVoucherListColumn}. None of these is mandatory (all appear in AP's
 * "Columns" chooser).
 *
 * <p>{@code SLID}'s data type is assumed {@link ColumnDataType#STRING} (sub-ledger IDs are
 * often alphanumeric) — confirm against real data if it's actually numeric.
 */
public enum APVoucherListColumn implements IVoucherListColumn {

    PAYMENT_AMT_CR("Payment Amt (Cr)", ColumnDataType.DECIMAL, false),
    ON_ACCOUNT("On Account", ColumnDataType.BOOLEAN, false),
    SLID("SLID", ColumnDataType.STRING, false),
    SL_NAME("SL Name", ColumnDataType.STRING, false),
    BRS_DATE("BRS Date", ColumnDataType.DATE, false);

    private final String headerText;
    private final ColumnDataType dataType;
    private final boolean mandatory;

    APVoucherListColumn(String headerText, ColumnDataType dataType, boolean mandatory) {
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
