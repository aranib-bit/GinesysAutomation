package com.gsl.pages.enums.Finance.Setup;

import com.gsl.enums.ISubModule;

/**
 * Finance module's Setup side-menu items (Chart of Accounts / TDS / Cheque / Budget / Opening
 * Entries) — each constant pairs the menu's human-readable name with its app-internal menu short
 * code and the side-menu group/section it's listed under, per {@link ISubModule}. Used to drive
 * Setup-side navigation (see {@code NavigationUtils#navigateToFinanceSetupSideMenu}) and, via
 * {@code SubModuleResolver}, to resolve a test's declared sub-module short code back to a constant.
 */
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
