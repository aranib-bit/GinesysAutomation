package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

/**
 * Grid column headers specific to AP Voucher's Sub-Ledger Information grid — columns shared
 * identically across AP/AR/General Voucher (e.g. Reference Site, Sub Ledger Id) live in
 * {@code CommonVoucherGridColumnHeader} instead.
 */
public enum APVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub-Ledger"),
    /** Column hosting the per-row TDS-deduction Yes/No trigger in AP's Sub-Ledger Information
     *  grid (see {@code APVoucherSubLedgerInformation}'s {@code DEDUCT_TDS_TRIGGER}). That class
     *  locates the cell itself by the literal itemId {@code deductTDS} (its own
     *  {@code COL_DEDUCT_TDS}), not via this constant's {@link #columnIdXPath()}. */
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

    @Override
    public String headerText() { return headerText; }
}
