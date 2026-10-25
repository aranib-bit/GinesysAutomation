package com.gsl.pages.enums.Finance.Operations.GeneralVoucher;

import com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;

import static com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind.DEFAULT;
import static com.gsl.pages.enums.Finance.Operations.Common.GridColumnKind.USER;

/**
 * Every column of General Voucher's Expense/Income Detail grid — the single source the UI-contract
 * validation loops over ({@code GeneralVoucherExpenseIncomeDetail#validateExpenseIncomeGridUi}).
 * Columns shared identically across AP/AR/General Voucher (e.g. Reference Site) live in
 * {@code CommonVoucherGridColumnHeader} instead. The Site and Cheque/RTGS columns have no header
 * text — they are row buttons, handled separately.
 *
 * <p>{@link GridColumnKind#DEFAULT} columns are always displayed (absence = FAIL);
 * {@link GridColumnKind#USER} columns are shown/hidden by the user via the grid column menu
 * (absence = SKIP "not displayed", presence = validated). The pre-existing fill/read methods in
 * {@code GeneralVoucherExpenseIncomeDetail} still use their own raw string literals for header text;
 * left as-is to avoid touching live-proven code.
 *
 * <p><b>Lowercase "ledger" in "General ledger" is correct</b> — confirmed live and matching
 * {@code GeneralVoucherExpenseIncomeDetail#getGeneralLedger}'s own proven literal (xpath text match
 * is case-sensitive).
 */
public enum GeneralVoucherGridColumnHeader implements IVoucherGridColumnHeader {

    GENERAL_LEDGER("General ledger", DEFAULT, false),
    LEDGER_GROUP_NAME("Ledger Group name", USER, false),
    LEDGER_ABBREVIATION("Ledger Abbreviation", USER, false),
    LEDGER_BALANCE("Ledger Balance", USER, true),
    SUB_LEDGER("Sub Ledger", DEFAULT, false),
    SUB_LEDGER_ID("Sub Ledger Id", USER, false),
    SUB_LEDGER_CLASS_NAME("Sub Ledger Class Name", USER, false),
    SUB_LEDGER_BALANCE("Sub Ledger Balance", USER, true),
    LEDGER_NARRATION("Ledger Narration", DEFAULT, false),
    IS_REVERSAL("Is Reversal", DEFAULT, false),
    EXPENSE_DR("Expense (Dr)", DEFAULT, true),
    INCOME_CR("Income (Cr)", DEFAULT, true);

    private final String headerText;
    private final GridColumnKind kind;
    private final boolean numeric;

    GeneralVoucherGridColumnHeader(String headerText, GridColumnKind kind, boolean numeric) {
        this.headerText = headerText;
        this.kind = kind;
        this.numeric = numeric;
    }

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

    @Override
    public GridColumnKind kind() { return kind; }

    @Override
    public boolean numeric() { return numeric; }
}
