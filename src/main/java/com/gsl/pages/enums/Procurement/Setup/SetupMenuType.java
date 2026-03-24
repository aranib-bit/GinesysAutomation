package com.gsl.pages.enums.Procurement.Setup;

public enum SetupMenuType {
    VENDOR("Vendor"),
    PRICE_CHART("Price Chart"),
    MARGIN_RULE("Margin Rule"),
    MARGIN_RULE_ALLOCATION_SITE_WISE("Margin Rule Allocation (Site Wise) "),
    MARGIN_RULE_ALLOCATION_VENDOR_WISE("Margin Rule Allocation (Vendor Wise) "),
    ASSIGN_ORGANIZATION_SITE("Assign Organization Site"),
    SERVICE("Service"),
    CHARGE("Charge"),
    TERMS("Terms"),
    FORMS("Forms"),
    TRADE_GROUP("Trade Group");

    private final String name;

    public String getName() {
        return name;
    }

    SetupMenuType(String name) {
        this.name = name;
    }
}
