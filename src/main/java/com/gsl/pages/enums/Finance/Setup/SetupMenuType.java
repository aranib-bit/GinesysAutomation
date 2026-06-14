package com.gsl.pages.enums.Finance.Setup;

import com.gsl.enums.ISubModule;

public enum SetupMenuType implements ISubModule {

    ACCOUNTGROUP       ("Account Group",              "financeaccountgrp",       "Setup › Chart of Accounts"),
    ACCOUNTCLASS       ("Account Class",              "FinaceAccountClass",       "Setup › Chart of Accounts"),
    LEDGER             ("Ledger",                     "SLLedger",                "Setup › Chart of Accounts"),
    SUBLEDGEROTHERS    ("Sub Ledger - Others",        "SLOthers",                "Setup › Chart of Accounts"),
    SUBLEDGEREMPLOYEES ("Sub Ledger - Employees",     "SLEmployees",             "Setup › Chart of Accounts"),
    NARRATION          ("Narration",                  "financenarration",        "Setup › Chart of Accounts"),
    TDSSOURCE          ("TDS Source",                 "FinanceTDSSource",        "Setup › TDS"),
    TDSSECTION         ("TDS Section",                "FinanceTDSSection",       "Setup › TDS"),
    TDSAGENCY          ("TDS Agency",                 "FinanceTDSAgency",        "Setup › TDS"),
    CHEQUEBOOK         ("Cheque Book",                "FinanceChequeBook",       "Setup › Cheque"),
    PRINTCHEQUE        ("Print Cheque",               "FinancePrintCheque",      "Setup › Cheque"),
    FINANCIALBUDGET    ("Financial Budget",           "FinanceFinancialBudget",  "Setup › Budget"),
    OPENINGLEDGER      ("Opening Entry - Ledger",     "OpeningLedger",           "Setup › Opening Entries"),
    OPENINGSUBLEDGER   ("Opening Entry - Sub Ledger", "OpeningSubLedger",        "Setup › Opening Entries");

    private final String name;
    private final String shortCode;
    private final String group;

    SetupMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
