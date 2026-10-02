package com.gsl.pages.enums.Admin;

/**
 * The Admin module's left-hand side-menu entries (Organization/Finance/Security/Utilities setup
 * areas) — consumed by {@code AdminTopMenuComponent.navigateToSubMenu(MainMenuType)} to click the
 * matching side-menu item and return the corresponding sub-module page object.
 */
public enum MainMenuType {
    ORGANIZATION("Organization"),
    FINANCE("Finance"),
    SECURITY("Security"),
    UTILITIES("Utilities");

    private final String name;

    /** @return the side-menu item's literal display text, e.g. "Organization". */
    public String getName(){
        return name;
    }

    MainMenuType(String name){
        this.name=name;
    }
}
