package com.gsl.pages.enums.Inventory.Operations;

import com.gsl.enums.ISubModule;

public enum OperationsMenuType implements ISubModule {

    REQUISITIONS_ENTRY  ("Requisition Entry",    "InvReqEnt",       "Requisitions"),
    MISCELLANEOUS_ENTRY ("Miscellaneous Entry",  "InvMiscEntry",    "Requisitions"),
    STOCK_POINT_TRANSFER("Stock Point Transfer", "InvStkPointTrn",  "Transfer"),
    AUDIT_PLAN          ("Audit Plan",           "InvStkAdtPln",    "Stock Audit"),
    AUDIT_ENTRY         ("Audit Entry",          "InvStkAdtEnt",    "Stock Audit"),
    BUNDLE              ("Bundle",               "InvBundle",       "Conversion"),
    PRODUCTION          ("Production",           "InvProduction",   "Conversion"),
    SPLIT               ("Split",                "InvSplit",        "Conversion");

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
