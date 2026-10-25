package com.gsl.pages.enums.Finance.Operations.APVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

/**
 * Grid column headers specific to AP Voucher's Sub-Ledger Information grid — columns shared
 * identically across AP/AR/General Voucher (e.g. Reference Site, Sub Ledger Id) live in
 * {@code CommonVoucherGridColumnHeader} instead.
 */
public enum APVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub-Ledger"),
    /** Confirmed live (user screenshot, 2026-10-06): the AP Sub Ledger Information grid shows
     *  Sub-Ledger, General Ledger, Reference Site, Amount (Dr), [Adjust], Ledger Narration, [Cheque/RTGS]. */
    GENERAL_LEDGER("General Ledger"),
    AMOUNT_DR("Amount (Dr)", GridColumnKind.DEFAULT, true),
    LEDGER_NARRATION("Ledger Narration"),
    /** Column hosting the per-row TDS-deduction Yes/No trigger in AP's Sub-Ledger Information
     *  grid (see {@code APVoucherSubLedgerInformation}'s {@code DEDUCT_TDS_TRIGGER}). That class
     *  locates the cell itself by the literal itemId {@code deductTDS} (its own
     *  {@code COL_DEDUCT_TDS}), not via this constant's {@link #columnIdXPath()}. */
    DEDUCT_TDS("Deduct TDS", GridColumnKind.USER, false);
    // REFERENCE_SITE and SUB_LEDGER_ID are identical across all domains — see CommonVoucherGridColumnHeader

    private final String headerText;
    private final GridColumnKind kind;
    private final boolean numeric;

    APVoucherGridColumnHeader(String headerText) {
        this(headerText, GridColumnKind.DEFAULT, false);
    }

    APVoucherGridColumnHeader(String headerText, GridColumnKind kind, boolean numeric) {
        this.headerText = headerText;
        this.kind = kind;
        this.numeric = numeric;
    }

    @Override
    public GridColumnKind kind() { return kind; }

    @Override
    public boolean numeric() { return numeric; }

    /** Ledger Narration is declared with {@code itemId: 'ledgerNarrationCode'} (proven live on GV, see
     *  {@code GridCellLocator}); every other column is addressed by its header id. */
    @Override
    public String itemId() {
        return name().equals("LEDGER_NARRATION") ? "ledgerNarrationCode" : null;
    }

    @Override
    public String columnIdXPath() {
        return "//span[normalize-space()='" + headerText
                + "']/ancestor::div[contains(@id,'gridcolumn')]/@id";
    }

    @Override
    public String headerText() { return headerText; }
}
