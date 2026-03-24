package com.gsl.pages.enums.Inventory.Product_Definition;

public enum Product_DefintionMenuType {

    GROUP("Group"),
    ARTICLE("Article"),
    ITEM("Item"),
    CATEGORY("Category"),
    ATTRIBUTE("Attribute"),
    SEASON("Season"),
    CONVERSION_TYPE("Conversion Type"),
    PRINT_BARCODE_LABEL("Print Barcode Label"),
    PROCESS("Process"),
    ITEM_SETS("Item Sets"),
    HSNSAC_MASTER("HSN/SAC Master"),
    GST_RATE("GST Rate"),
    TAX_REGION("Tax Region"),
    TAX_GROUP("Tax Group"),
    TAX_RATES("Tax Rates"),
    EXCISE_TARIFF("Excise Tariff"),
    GST_ADDITIONAL_CESS("GST Additional Cess"),
    STOCK_POINT_GROUP("Stock Point Group"),
    STOCK_POINT("Stock Point"),
    OPENING_STOCK("Opening Stock");

    private final String name;

    public String getName() {
        return name;
    }

    Product_DefintionMenuType(String name) {
        this.name = name;
    }
}

