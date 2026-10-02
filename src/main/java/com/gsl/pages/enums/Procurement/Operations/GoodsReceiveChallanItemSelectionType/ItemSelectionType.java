package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

import org.openqa.selenium.By;

/**
 * The "item vs. set" item-selection method offered on the Goods Receive Challan Add screen's item
 * selector (see {@code GoodsReceiveChallanItemDetails#clickItemSelector(ItemSelectionType)} and
 * {@code GoodsReceiveChallanAdd}) — each constant is one clickable option on that selector, e.g.
 * "Scan Item" vs. "Select Item" vs. "Copy Challan" from a previous document. Test data names a
 * constant via its {@code itemSetSelectionWay} column (see {@code MergedReceiptDataSupplier},
 * {@link ItemSelectionTypeEnumMapper}). The sibling {@link SetSelectionType} enum covers the
 * separate "set" major-method options instead of this "item" major-method's options.
 */
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

    /** This option's visible selector text, e.g. {@code "Scan Item"}. */
    public String getLabel() {
        return label;
    }

    /**
     * Resolves {@code text} to a constant, matching against either the visible {@link #getLabel()}
     * text or the constant's own name with underscores stripped (both case-insensitive).
     *
     * @throws IllegalArgumentException if {@code text} matches no constant
     */
    public static ItemSelectionType fromString(String text) {
        for (ItemSelectionType b : ItemSelectionType.values()) {
            if (b.label.equalsIgnoreCase(text) || b.name().replace("_", "").equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
