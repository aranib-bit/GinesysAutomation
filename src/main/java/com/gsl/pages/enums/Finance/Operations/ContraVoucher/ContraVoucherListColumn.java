package com.gsl.pages.enums.Finance.Operations.ContraVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;

/**
 * List-grid columns specific to Contra Voucher.
 *
 * <p>TODO: Contra Voucher has no live list page/DOM today (menu-placeholder only — see
 * {@code VoucherDomain.CONTRA}, whose {@code getSubLedgerColumnHeader()} likewise throws
 * {@link UnsupportedOperationException} rather than guessing). This enum has no constants
 * yet; add them once a real Contra Voucher list page exists to confirm columns against.
 */
public enum ContraVoucherListColumn implements IVoucherListColumn {
    ;

    private final String headerText;
    private final ColumnDataType dataType;
    private final boolean mandatory;

    ContraVoucherListColumn(String headerText, ColumnDataType dataType, boolean mandatory) {
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
