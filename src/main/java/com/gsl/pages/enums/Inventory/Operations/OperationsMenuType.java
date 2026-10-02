package com.gsl.pages.enums.Inventory.Operations;

import com.gsl.enums.ISubModule;

/**
 * Inventory &gt; Operations' side-menu sub-items (grouped under Requisitions/Transfer/Stock
 * Audit/Conversion headers) — registered in {@code SubModuleResolver}'s {@code INVENTORY} entry
 * so a test's declared {@code fw.subModule} short code (e.g. {@code "InvReqEnt"}) resolves back
 * to one of these constants.
 * <p>TODO-confirm: {@code com.gsl.pages.pagecomponent.Inventory.Operations.OperationsSideMenuComponent
 * .clickMenuItem(...)} currently takes a {@link
 * com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType} parameter, not this
 * enum — no direct side-menu-click consumer of {@code OperationsMenuType} was found; it appears
 * to be wired only through {@code SubModuleResolver} today.
 */
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
