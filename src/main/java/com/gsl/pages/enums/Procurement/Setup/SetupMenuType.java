package com.gsl.pages.enums.Procurement.Setup;

import com.gsl.enums.ISubModule;

/**
 * Procurement module's Setup side-menu items (Vendors / Services / Invoice Overheads) — each
 * constant pairs the menu's human-readable name with its app-internal menu short code and the
 * side-menu group/section it's listed under, per {@link ISubModule}. Used to drive Setup-side
 * navigation (see {@code SetupSideMenuComponent}) and, via {@code SubModuleResolver}, to resolve a
 * test's declared sub-module short code back to a constant.
 */
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
