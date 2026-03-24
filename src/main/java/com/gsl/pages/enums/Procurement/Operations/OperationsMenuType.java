package com.gsl.pages.enums.Procurement.Operations;

public enum OperationsMenuType {
    PURCHASE_ORDER("Purchase Order"),
    CANCEL_PURCHASE_ORDER("Cancel Purchase Order"),
    RECEIPTS("Receipts"),
    RETURNS("Returns"),
    PURCHASE_INVOICE("Purchase Invoice"),
    PURCHASE_RETURN("Purchase Return"),
    DEBIT_NOTE("Debit Note"),
    CREDIT_NOTE("Credit Note"),
    SERVICE_ORDER("Service Order"),
    CANCEL_SERVICE_ORDER("Cancel Service Order"),
    SERVICE_INVOICE_ADHOC("Service Invoice Adhoc"),
    SERVICE_INVOICE_AGAINST_DOCUMENT("Service Invoice Against Document"),
    SERVICE_DEBIT_NOTE("Service Debit Note"),
    DN_CN_RECONCILIATION("DN/CN Reconciliation");

    private final String name;

    public String getName() {
        return name;
    }

    OperationsMenuType(String name) {
        this.name = name;
    }
}
