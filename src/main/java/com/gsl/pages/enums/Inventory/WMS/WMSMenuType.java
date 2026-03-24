package com.gsl.pages.enums.Inventory.WMS;

public enum WMSMenuType {
    PUT_AWAY("Put Away"),
    TAKE_AWAY("Take Away"),
    BIN_ADJUSTMENT("Bin Adjustment"),
    STOCK_RESERVATION("Stock Reservation"),
    PICK_LIST("Pick List"),
    CONFIRM_PICK_LIST("Confirm Pick List"),
    BIN_GROUP_MASTER("Bin Group Master"),
    BIN_ASSORTMENT("Bin Assortment"),
    BIN_MASTER("Bin Master");

    private final String name;

    public String getName() {
        return name;
    }

    WMSMenuType(String name) {
        this.name = name;
    }
}
