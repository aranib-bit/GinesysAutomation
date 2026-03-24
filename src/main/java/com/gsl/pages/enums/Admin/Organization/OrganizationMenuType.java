package com.gsl.pages.enums.Admin.Organization;

public enum OrganizationMenuType {
    ORGANIZATION_UNIT("Organization Unit"),
    ORGANIZATION_GROUP("Organization Group"),
    ORGANIZATION_SITE("Organization Site"),
    GSTIN("GST Identification Number (GSTIN)"),
    GST_STATE("GST State"),
    CITY("City"),
    ACCOUNTING_YEAR("Accounting Year"),
    INVENTORY_MONTHS("Inventory Months"),
    FINANCE_MONTHS("Finance Months");

    private final String name;

    OrganizationMenuType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
