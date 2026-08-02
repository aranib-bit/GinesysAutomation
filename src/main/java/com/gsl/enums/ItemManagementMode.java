package com.gsl.enums;

/** Item management mode, as sent in ItemMaster/Create's itemManagementMode/priceManagement fields. */
public enum ItemManagementMode {
    INDIVIDUAL("I", "I"),
    BATCH("B", "B"),
    SERIAL("S", "B");

    private final String itemManagementCode;
    private final String priceManagementCode;

    ItemManagementMode(String itemManagementCode, String priceManagementCode) {
        this.itemManagementCode = itemManagementCode;
        this.priceManagementCode = priceManagementCode;
    }

    public String itemManagementCode() { return itemManagementCode; }
    public String priceManagementCode() { return priceManagementCode; }
}
