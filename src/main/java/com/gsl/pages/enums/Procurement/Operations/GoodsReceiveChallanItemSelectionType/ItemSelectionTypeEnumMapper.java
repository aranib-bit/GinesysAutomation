package com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType;

/**
 * Maps a free-text {@code itemSetSelectionWay} test-data value (see {@code MergedReceiptTest},
 * {@code MergedReceiptDataSupplier}) to its {@link ItemSelectionType} constant. Unlike {@link
 * ItemSelectionType#fromString(String)}, this only recognizes the two "item" major-method values
 * actually driven through this mapper today — {@code "Scan Item"}/{@code "Select Item"} — and
 * rejects everything else, including {@code "Select Set"} (that value's {@link SetSelectionType}
 * mapping is commented out below, not yet wired through this mapper).
 */
public class ItemSelectionTypeEnumMapper {
    /**
     * @param value the raw {@code itemSetSelectionWay} cell text, case/whitespace-insensitive
     * @throws IllegalArgumentException if {@code value} is {@code null} or names anything other
     *                                   than {@code "Scan Item"}/{@code "Select Item"}
     */
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
