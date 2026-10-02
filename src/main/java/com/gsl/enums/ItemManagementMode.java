package com.gsl.enums;

/** Item management mode, as sent in ItemMaster/Create's itemManagementMode/priceManagement fields. */
public enum ItemManagementMode {
    INDIVIDUAL("I", "I"),
    BATCH("B", "B"),
    /** Serial-tracked items price-manage as Batch ("B") — there is no separate "S" price-management
     *  code; only {@code itemManagementCode} is "S" here. */
    SERIAL("S", "B");

    private final String itemManagementCode;
    private final String priceManagementCode;

    ItemManagementMode(String itemManagementCode, String priceManagementCode) {
        this.itemManagementCode = itemManagementCode;
        this.priceManagementCode = priceManagementCode;
    }

    /** Value sent as {@code itemManagementMode} in {@code ItemCreateRequest}. */
    public String itemManagementCode() { return itemManagementCode; }
    /** Value sent as {@code priceManagement} in {@code ItemCreateRequest}. */
    public String priceManagementCode() { return priceManagementCode; }
}
