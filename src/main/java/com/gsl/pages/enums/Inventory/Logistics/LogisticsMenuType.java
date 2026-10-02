package com.gsl.pages.enums.Inventory.Logistics;

import com.gsl.enums.ISubModule;

/**
 * Inventory &gt; Logistics' side-menu sub-items (grouped under Incoming/Outgoing headers) —
 * registered in {@code SubModuleResolver}'s {@code INVENTORY} entry so a test's declared
 * {@code fw.subModule} short code (e.g. {@code "InvGateIn"}) resolves back to one of these
 * constants; mirrors how {@link com.gsl.pages.enums.Finance.Operations.OperationsMenuType} is
 * consumed for Finance.
 */
public enum LogisticsMenuType implements ISubModule {

    INCOMING_LR ("LR",         "InvLRIn",   "Incoming"),
    GATE_ENTRY  ("Gate Entry", "InvGateIn", "Incoming"),
    /** Same display text ("LR") as {@link #INCOMING_LR} — the two are disambiguated by group
     *  ("Incoming" vs "Outgoing") and shortcode, not by name alone. */
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
