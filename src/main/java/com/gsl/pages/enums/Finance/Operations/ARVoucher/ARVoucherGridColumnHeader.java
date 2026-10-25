package com.gsl.pages.enums.Finance.Operations.ARVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

/**
 * Grid column headers specific to AR Voucher's Sub-Ledger Information grid — columns shared
 * identically across AR/AP/General Voucher (e.g. Reference Site, Sub Ledger Id) live in
 * {@code CommonVoucherGridColumnHeader} instead.
 */
public enum ARVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    SUB_LEDGER("Sub Ledger", GridColumnKind.DEFAULT, false),
    /** UNVERIFIED header texts (mirrors AP's live-confirmed grid) — USER so a wrong text is a SKIP, not a FAIL,
     *  until confirmed against the live AR grid. */
    GENERAL_LEDGER("General Ledger", GridColumnKind.USER, false),
    AMOUNT_CR("Amount (Cr)", GridColumnKind.USER, true),
    LEDGER_NARRATION("Ledger Narration", GridColumnKind.USER, false);
    // REFERENCE_SITE and SUB_LEDGER_ID are identical across all domains — see CommonVoucherGridColumnHeader

    private final String headerText;

    private final GridColumnKind kind;
    private final boolean numeric;

    ARVoucherGridColumnHeader(String headerText, GridColumnKind kind, boolean numeric) {
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
