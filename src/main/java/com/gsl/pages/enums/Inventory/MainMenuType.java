package com.gsl.pages.enums.Inventory;

public enum MainMenuType {
    OPERATIONS("Operations"),
    WMS("WMS"),
    PRODUCT_DEFINITION("Product Definition"),
    LOGISTICS("Logistics");

    private final String name;

    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
