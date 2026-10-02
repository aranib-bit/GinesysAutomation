package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

/**
 * The "set" item-selection method offered on the Goods Receive Challan Add screen's item selector
 * — sibling of {@link ItemSelectionType}, which covers the "item" major-method's options instead.
 * Test data selects this enum (over {@code ItemSelectionType}) when a row's {@code
 * itemSelectionMajorMethod} column is {@code "Set"} (see {@code MergedReceiptDataSupplier}).
 */
public enum SetSelectionType {
    SELECT_SET ("ITEM Sets");

    private final String name;

    /** This option's visible selector text, e.g. {@code "ITEM Sets"}. */
    public String getName(){
        return name;
    }

    SetSelectionType(String name){
        this.name=name;
    }
}
