package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

public class ItemSelectionTypeEnumMapper {
    public static ItemSelectionType mapItemSelection(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Item Selection Way cannot be null");
        }
        switch (value.trim().toLowerCase()) {
            case "scan item": return ItemSelectionType.SCAN_ITEM;
            case "select item": return ItemSelectionType.SELECT_ITEM;
            //case "select set": return SetSelectionType.SELECT_SET;
            default:
                throw new IllegalArgumentException("Unknown ItemSelectionType: " + value);
        }
    }
}
