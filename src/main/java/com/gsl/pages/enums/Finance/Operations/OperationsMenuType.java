package com.gsl.pages.enums.Finance.Operations;

import com.gsl.enums.ISubModule;

public enum OperationsMenuType implements ISubModule {

    ARVOUCHER     ("AR Voucher",          "FinanceARVoucher",     "Vouchers"),
    APVOUCHER     ("AP Voucher",          "FinanceAPVoucher",     "Vouchers"),
    ITEM          ("Contra Voucher",      "FinanceConVoucher",    "Vouchers"),
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
