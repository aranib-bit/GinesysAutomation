package com.gsl.pages.pagecomponent.Admin.Organization;

import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionSideMenuComponent;

/**
 * Landing page object for Admin's Organization sub-menu — returned by {@link
 * com.gsl.pages.pagecomponent.Admin.AdminTopMenuComponent#navigateToSubMenu} for {@link
 * com.gsl.pages.enums.Admin.MainMenuType#ORGANIZATION}. A thin holder for {@link
 * OrganizationSideMenuComponent}, the component that drives Organization's own left side-menu —
 * reached via {@link #getOrganizationSideMenuComponent()}.
 */
public class OrganizationHomePage {
    private OrganizationSideMenuComponent organizationSideMenuComponent;

    /** Eagerly constructs this page's {@link OrganizationSideMenuComponent}. */
    public OrganizationHomePage() {
        this.organizationSideMenuComponent=new OrganizationSideMenuComponent();
    }

    /** @return this page's {@link OrganizationSideMenuComponent}, for navigating the side-menu. */
    public OrganizationSideMenuComponent getOrganizationSideMenuComponent() {
        return organizationSideMenuComponent;
    }
}
