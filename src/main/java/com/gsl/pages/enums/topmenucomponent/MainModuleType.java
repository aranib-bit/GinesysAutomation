package com.gsl.pages.enums.topmenucomponent;

/**
 * The application's top-level module switcher entries (Admin/Inventory/Procurement/Finance/Sales
 * &amp; Distribution/POS Management/Report) — the module tabs shown in the app's own top nav bar.
 * Consumed by {@code TopModuleMenuComponent.navigateToModule(MainModuleType)} to click the
 * matching top-level tab and, for a module with a confirmed dashboard header (see {@link
 * DashboardTitle#forModule(MainModuleType)}), verify the resulting dashboard loaded. Also the key
 * type for {@code SubModuleResolver}'s per-module {@code *MenuType} registry and for {@link
 * DashboardTitle}.
 */
public enum MainModuleType {
    ADMIN("Admin"),
    INVENTORY("Inventory"),
    PROCUREMENT("Procurement"),
    FINANCE("Finance"),
    SALESDISTRUBTION("Sales & Distribution"),
    POSMANAGEMENT("POS Management"),
    REPORT("Report");

    private final String name;

    /** @return the module tab's literal display text, e.g. "Sales & Distribution". */
    public String getName(){
        return name;
    }

    MainModuleType(String name){
        this.name=name;
    }
}
