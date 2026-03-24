package com.gsl.pages.enums.Finance.Setup;

public enum SetupMenuType {
    ACCOUNTGROUP("Account Group"),
    APVOUCHER("Account Class"),
    LEDGER("Ledger"),
    SUBLEDGEROTHERS("Sub Ledger - Others"),
    SUBLEDGEREMPLOYEES("Sub Ledger - Employees"),
    NARRATION("Narration"),
    TDSSOURCE("TDS Source"),
    TDSSECTION("TDS Section"),
    TDSAGENCY("TDS Agency"),
    CHEQUEBOOK("Cheque Book"),
    PRINTCHEQUE("Print Cheque"),
    FINANCIALBUDGET("Financial Budget"),
    OPENINGLEDGER("Ledger"),
    OPENINGSUBLEDGER("Sub Ledger");

    private final String name;

    public String getName() {
        return name;
    }

    SetupMenuType(String name) {
        this.name = name;
    }
}
