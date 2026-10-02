package com.gsl.pages.pagecomponent.Admin;

import com.gsl.pages.pagecomponent.Admin.AdminTopMenuComponent;

/**
 * Landing page object for the Admin module — returned by {@link
 * com.gsl.pages.pagecomponent.TopModuleMenuComponent#navigateToModule(com.gsl.pages.enums.topmenucomponent.MainModuleType)}
 * once the Admin tab is active. A thin holder for {@link AdminTopMenuComponent}, the component
 * that drives Admin's own sub-menu (Organization/Finance/Security/Utilities) — reached via
 * {@link #getAdminTopMenuComponent()}.
 */
public class AdminHomePage {

    private AdminTopMenuComponent adminTopMenuComponent;

    /** Eagerly constructs this page's {@link AdminTopMenuComponent}. */
    public AdminHomePage() {
        this.adminTopMenuComponent = new AdminTopMenuComponent();
    }

    /** @return this page's {@link AdminTopMenuComponent}, for navigating Admin's sub-menus. */
    public AdminTopMenuComponent getAdminTopMenuComponent() {
        return adminTopMenuComponent;
    }
}
