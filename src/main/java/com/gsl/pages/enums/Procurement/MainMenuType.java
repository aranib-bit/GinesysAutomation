package com.gsl.pages.enums.Procurement;

/**
 * The Procurement module's top-menu sub-tabs (Operations/Setup) — consumed by {@code
 * ProcurementTopMenuComponent.navigateToSubMenu(MainMenuType, Class)} (via {@code
 * NavigationUtils.navigateToProcurementSideMenu}) to click the matching tab before drilling into
 * a side-menu item (e.g. {@link
 * com.gsl.pages.enums.Procurement.Operations.OperationsMenuType}).
 */
public enum MainMenuType {
    OPERATIONS("Operations"),
    SETUP("Setup");

    private final String name;

    /** @return the top-menu tab's literal display text, e.g. "Operations". */
    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
