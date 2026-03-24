package com.gsl.pages.enums.Finance.Operations;

public enum OperationsMenuType {
    ARVOUCHER("AR Voucher"),
    APVOUCHER("AP Voucher"),
    ITEM("Contra Voucher"),
    CATEGORY("General Voucher"),
    BANKRECONCILIATION("Bank Reconciliation"),
    GENERALJOURNAL("General Journal"),
    ARAPJOURNAL("AR/AP Journal"),
    DEBITJOURNAL("Debit Journal"),
    CREDITJOURNAL("Credit Journal"),
    DOCUMENTADJUSTMENT("Document Adjustment"),
    POSJOURNAL("POS Journal"),
    GVJOURNALS("GV Journals"),
    TDSJOURNAL("TDS Journal"),
    TDSREVERSALJOURNAL("TDS Reversal Journal");

    private final String name;

    public String getName() {
        return name;
    }

    OperationsMenuType(String name) {
        this.name = name;
    }
}
