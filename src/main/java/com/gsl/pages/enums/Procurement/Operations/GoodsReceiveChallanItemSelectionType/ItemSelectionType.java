package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

import org.openqa.selenium.By;

public enum ItemSelectionType {
    CREATE_ITEM("Create Item"),
    SELECT_ITEM("Select Item"),
    SCAN_ITEM("Scan Item"),
    IMPORT_BARCODE("Import Barcode"),
    IMPORT_EXCEL("Import Excel"),
    ITEM_COMBINATION("Item Combination"),
    SELECT_ORDER_ITEMS("Order Items"),
    ALTERNATE_ORDER_ITEMS("Alternate Order Items"),
    COPY_CHALLAN("Copy Challan"),
    IMPORT_SET_BARCODE("Import Set Barcode");

    private final String label;

    ItemSelectionType(String label){
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ItemSelectionType fromString(String text) {
        for (ItemSelectionType b : ItemSelectionType.values()) {
            if (b.label.equalsIgnoreCase(text) || b.name().replace("_", "").equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
