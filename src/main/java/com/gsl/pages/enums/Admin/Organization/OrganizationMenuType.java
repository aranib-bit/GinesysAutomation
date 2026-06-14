package com.gsl.pages.enums.Admin.Organization;

import com.gsl.enums.ISubModule;

public enum OrganizationMenuType implements ISubModule {

    ORGANIZATION_UNIT   ("Organization Unit",                 "AdminOrganizationUnit",   "Hierarchy"),
    ORGANIZATION_GROUP  ("Organization Group",                "AdminOrganizationGroup",  "Hierarchy"),
    ORGANIZATION_SITE   ("Organization Site",                 "AdminOrganizationSite",   "Hierarchy"),
    GSTIN               ("GST Identification Number (GSTIN)", "gstin",                   "Manage"),
    GST_STATE           ("GST State",                         "gststate",                "Explore"),
    CITY                ("City",                              "AdminCity",               "Explore"),
    ACCOUNTING_YEAR     ("Accounting Year",                   "AdminAccountingYear",     "Periods"),
    INVENTORY_MONTHS    ("Inventory Months",                  "InventoryMonths",         "Periods"),
    FINANCE_MONTHS      ("Finance Months",                    "FinancialMonths",         "Periods");

    private final String name;
    private final String shortCode;
    private final String group;

    OrganizationMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
