package com.gsl.pages.enums.Finance.Operations;

import com.gsl.enums.ISubModule;

/**
 * Finance &gt; Operations' side-menu sub-items (grouped under Vouchers/Journals/Retail
 * Journals/TDS headers) — consumed by {@code
 * NavigationUtils.navigateToFinanceSideMenu(HomePage, MainMenuType, OperationsMenuType, Class)},
 * which passes it to {@code OperationsSideMenuComponent.clickMenuItem(ISubModule, Class)} to
 * expand the right group and click the matching item, then verify the resulting page. Also
 * registered in {@code SubModuleResolver}'s {@code FINANCE} entry so a test's declared {@code
 * fw.subModule} short code (e.g. {@code "FinanceARVoucher"}) resolves back to one of these
 * constants.
 */
public enum OperationsMenuType implements ISubModule {

    ARVOUCHER     ("AR Voucher",          "FinanceARVoucher",     "Vouchers"),
    APVOUCHER     ("AP Voucher",          "FinanceAPVoucher",     "Vouchers"),
    /** Constant name doesn't match its menu text/shortcode — this is the Contra Voucher entry, not an item master. */
    ITEM          ("Contra Voucher",      "FinanceConVoucher",    "Vouchers"),
    /** Constant name doesn't match its menu text/shortcode — this is the General Voucher entry, not a category master. */
    CATEGORY      ("General Voucher",     "FinanceGenVoucher",    "Vouchers"),
    BRS           ("Bank Reconciliation", "FinanceBRS",           "Vouchers"),
    GENJOURNAL    ("General Journal",     "FinanceGenJournal",    "Journals"),
    ARAPJOURNAL   ("AR/AP Journal",       "FinanceARAPJournal",   "Journals"),
    DEBITJOURNAL  ("Debit Journal",       "FinanceDebtJournal",   "Journals"),
    CREDITJOURNAL ("Credit Journal",      "FinanceCredJournal",   "Journals"),
    DOCADJUST     ("Document Adjustment", "FinanceDocAdjust",     "Journals"),
    POSJOURNAL    ("POS Journal",         "POSJournal",           "Retail Journals"),
    GVJOURNALS    ("GV Journals",         "GVJournal",            "Retail Journals"),
    TDSJOURNAL    ("TDS Journal",         "FinanceTDSJournal",    "TDS"),
    TDSREVJOURNAL ("TDS Reversal Journal","FinanceTDSRevJournal",  "TDS");

    private final String name;
    private final String shortCode;
    private final String group;

    OperationsMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
