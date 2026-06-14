package com.gsl.pages.enums.Inventory.Logistics;

import com.gsl.enums.ISubModule;

public enum LogisticsMenuType implements ISubModule {

    INCOMING_LR ("LR",         "InvLRIn",   "Incoming"),
    GATE_ENTRY  ("Gate Entry", "InvGateIn", "Incoming"),
    OUTGOING_LR ("LR",         "InvLROut",  "Outgoing");

    private final String name;
    private final String shortCode;
    private final String group;

    LogisticsMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
