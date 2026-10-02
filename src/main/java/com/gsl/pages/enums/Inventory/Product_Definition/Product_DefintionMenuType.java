package com.gsl.pages.enums.Inventory.Product_Definition;

import com.gsl.enums.ISubModule;

/**
 * Inventory &gt; Product Definition's side-menu sub-items (grouped under Item Hierarchy/Setup/Tax
 * Definition/Stock Points headers) — consumed by {@code
 * ProductDefinitionSideMenuComponent.clickMenuItem(Product_DefintionMenuType, Class)} (via {@code
 * NavigationUtils.navigateToInventorySideMenu}) to expand the right group and click the matching
 * item. Also registered in {@code SubModuleResolver}'s {@code INVENTORY} entry so a test's
 * declared {@code fw.subModule} short code (e.g. {@code "InvItem"}) resolves back to one of these
 * constants.
 */
public enum Product_DefintionMenuType implements ISubModule {

    GROUP               ("Group",                 "InvGroup",            "Item Hierarchy"),
    ARTICLE             ("Article",               "InvArticleCreation",  "Item Hierarchy"),
    ITEM                ("Item",                  "InvItem",             "Item Hierarchy"),
    CATEGORY            ("Category",              "InvCategorymaster",   "Setup"),
    ATTRIBUTE           ("Attribute",             "InvAttribute",        "Setup"),
    SEASON              ("Season",                "InvSeason",           "Setup"),
    CONVERSION_TYPE     ("Conversion Type",       "conversiontype",      "Setup"),
    PRINT_BARCODE_LABEL ("Print Barcode Label",   "PrintBarcodeLabel",   "Setup"),
    PROCESS             ("Process",               "InvProcess",          "Setup"),
    ITEM_SETS           ("Item Sets",             "InvSet",              "Setup"),
    HSNSAC_MASTER       ("HSN/SAC Master",        "gsthsnsaccode",       "Tax Definition"),
    GST_RATE            ("GST Rate",              "gstrate",             "Tax Definition"),
    TAX_REGION          ("Tax Region",            "TaxRegion",           "Tax Definition"),
    TAX_GROUP           ("Tax Group",             "TaxGroup",            "Tax Definition"),
    TAX_RATES           ("Tax Rates",             "TaxRates",            "Tax Definition"),
    EXCISE_TARIFF       ("Excise Tariff",         "ExciseTariff",        "Tax Definition"),
    GST_ADDITIONAL_CESS ("GST Additional Cess",   "GstAddCessRt",        "Tax Definition"),
    STOCK_POINT_GROUP   ("Stock Point Group",     "InvStockPointGroup",  "Stock Points"),
    STOCK_POINT         ("Stock Point",           "InvStockPoint",       "Stock Points"),
    OPENING_STOCK       ("Opening Stock",         "InvOpeningStock",     "Stock Points");

    private final String name;
    private final String shortCode;
    private final String group;

    Product_DefintionMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
