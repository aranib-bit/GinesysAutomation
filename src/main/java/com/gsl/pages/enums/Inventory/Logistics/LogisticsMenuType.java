package com.gsl.pages.enums.Inventory.Logistics;

public enum LogisticsMenuType {
    INCOMING_LR("LR"),
    GATE_ENTRY("Gate Entry"),
    OUTGOING_LR("LR");

    private final String name;

    public String getName() {
        return name;
    }

    LogisticsMenuType(String name) {
        this.name = name;
    }
}
