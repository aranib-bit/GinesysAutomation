package com.gsl.pages.enums.Inventory;

/**
 * The Inventory module's top-menu sub-tabs (Operations/WMS/Product Definition/Logistics) —
 * consumed by {@code InventoryTopMenuComponent.navigateToSubMenu(MainMenuType, Class)} (via
 * {@code NavigationUtils.navigateToInventorySideMenu}) to click the matching tab before drilling
 * into a side-menu item (e.g. {@link
 * com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType}).
 */
public enum MainMenuType {
    OPERATIONS("Operations"),
    WMS("WMS"),
    PRODUCT_DEFINITION("Product Definition"),
    LOGISTICS("Logistics");

    private final String name;

    /** @return the top-menu tab's literal display text, e.g. "Product Definition". */
    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
