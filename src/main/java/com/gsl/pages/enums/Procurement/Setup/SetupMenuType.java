package com.gsl.pages.enums.Procurement.Setup;

import com.gsl.enums.ISubModule;

public enum SetupMenuType implements ISubModule {

    VENDOR                             ("Vendor",                               "Vendor",                    "Vendors"),
    PRICE_CHART                        ("Price Chart",                          "PriceChart",                "Vendors"),
    MARGIN_RULE                        ("Margin Rule",                          "MarginRule",                "Vendors"),
    MARGIN_RULE_ALLOCATION_SITE_WISE   ("Margin Rule Allocation (Site Wise)",   "MarginRuleAllocationSite",  "Vendors"),
    MARGIN_RULE_ALLOCATION_VENDOR_WISE ("Margin Rule Allocation (Vendor Wise)", "MarginRuleAllocationVen",   "Vendors"),
    ASSIGN_ORGANIZATION_SITE           ("Assign Organization Site",             "ProcurementAssignOrgSite",  "Vendors"),
    SERVICE                            ("Service",                              "ProcurementServiceMaster",  "Services"),
    CHARGE                             ("Charge",                               "ProcurementCharge",         "Invoice Overheads"),
    TERMS                              ("Terms",                                "ProcurementTerms",          "Invoice Overheads"),
    FORMS                              ("Forms",                                "PurchaseForm",              "Invoice Overheads"),
    TRADE_GROUP                        ("Trade Group",                          "ProcurementTradeGrp",       "Invoice Overheads");

    private final String name;
    private final String shortCode;
    private final String group;

    SetupMenuType(String name, String shortCode, String group) {
        this.name      = name;
        this.shortCode = shortCode;
        this.group     = group;
    }

    @Override public String getName()      { return name; }
    @Override public String getShortCode() { return shortCode; }
    @Override public String getGroup()     { return group; }
}
