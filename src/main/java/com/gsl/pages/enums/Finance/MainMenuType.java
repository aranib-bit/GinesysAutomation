package com.gsl.pages.enums.Finance;

/**
 * The Finance module's top-menu sub-tabs (Operations/Setup) — consumed by {@code
 * FinanceTopMenuComponent.navigateToSubMenu(MainMenuType, Class)} (via {@code
 * NavigationUtils.navigateToFinanceSideMenu}/{@code navigateToFinanceSetupSideMenu}) to click the
 * matching tab before drilling into a side-menu item (e.g. {@link
 * com.gsl.pages.enums.Finance.Operations.OperationsMenuType}).
 */
public enum MainMenuType {

    OPERATIONS("Operations"),
    SETUP("Setup");

    private final String name;

    MainMenuType(String name) {
        this.name = name;
    }

    /** @return the top-menu tab's literal display text, e.g. "Operations". */
    public String getName() {
        return name;
    }
}