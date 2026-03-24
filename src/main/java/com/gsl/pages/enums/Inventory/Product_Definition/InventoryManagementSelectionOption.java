package com.gsl.pages.enums.Inventory.Product_Definition;

public enum InventoryManagementSelectionOption {
    ITEM_WISE("Item Wise"),
    SERIAL_WISE("Serial Wise"),
    BATCH_WISE("Batch Wise");

    private final String value;

    InventoryManagementSelectionOption(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }


}
