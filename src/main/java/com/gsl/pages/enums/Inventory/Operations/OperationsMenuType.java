package com.gsl.pages.enums.Inventory.Operations;

public enum OperationsMenuType {

    REQUISITIONS_ENTRY("Requisition Entry"),
    MISCELLANEOUS_ENTRY("Miscellaneous Entry"),
    STOCK_POINT_TRANSFER("Stock Point Transfer"),
    AUDIT_PLAN("Audit Plan"),
    AUDIT_ENTRY("Audit Entry"),
    BUNDLE("Bundle"),
    PRODUCTION("Production"),
    SPLIT("Split");

    private final String name;

    public String getName() {
        return name;
    }

    OperationsMenuType(String name) {
        this.name = name;
    }
}
