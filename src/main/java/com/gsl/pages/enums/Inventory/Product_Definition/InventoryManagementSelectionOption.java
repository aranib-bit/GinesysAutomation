package com.gsl.pages.enums.Inventory.Product_Definition;

/**
 * The three "Inventory Management" tracking-mode options (Item Wise/Serial Wise/Batch Wise) on
 * the Item master's Product Definition setup — mirrors the app's own radio-button/dropdown
 * choices for how an item's stock is tracked.
 * <p>TODO-confirm: no current caller of this enum was found anywhere in the codebase — it does
 * not yet appear to be wired into an Item Add/Edit page object.
 */
public enum InventoryManagementSelectionOption {
    ITEM_WISE("Item Wise"),
    SERIAL_WISE("Serial Wise"),
    BATCH_WISE("Batch Wise");

    private final String value;

    InventoryManagementSelectionOption(String value) {
        this.value = value;
    }

    /** @return this option's literal display text, e.g. "Serial Wise". */
    public String getValue() {
        return value;
    }


}
