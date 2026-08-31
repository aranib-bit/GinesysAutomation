package com.gsl.pages.enums.topmenucomponent;

import java.util.Arrays;
import java.util.Optional;

/**
 * Single source of truth for each module's expected dashboard header title, used by
 * {@code TopModuleMenuComponent.navigateToModule()} to decide whether a post-click
 * dashboard-loaded verification applies to a given {@link MainModuleType}.
 * <p>
 * Only {@link #FINANCE} is populated today — its dashboard header DOM has been confirmed.
 * Add an entry for Inventory/Procurement/etc. here once their dashboard header DOM is
 * confirmed; no other code needs to change (the module lookup is generic).
 */
public enum DashboardTitle {

    FINANCE(MainModuleType.FINANCE, "Finance Dashboard");

    private final MainModuleType module;
    private final String expectedTitle;

    DashboardTitle(MainModuleType module, String expectedTitle) {
        this.module = module;
        this.expectedTitle = expectedTitle;
    }

    public MainModuleType getModule() {
        return module;
    }

    public String getExpectedTitle() {
        return expectedTitle;
    }

    /** @return the dashboard-title mapping for {@code module}, if one has been confirmed. */
    public static Optional<DashboardTitle> forModule(MainModuleType module) {
        return Arrays.stream(values()).filter(d -> d.module == module).findFirst();
    }
}
