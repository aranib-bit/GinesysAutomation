package com.gsl.pages.pagecomponent.Admin.Organization;

import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionSideMenuComponent;

public class OrganizationHomePage {
    private OrganizationSideMenuComponent organizationSideMenuComponent;

    public OrganizationHomePage() {
        this.organizationSideMenuComponent=new OrganizationSideMenuComponent();
    }

    public OrganizationSideMenuComponent getOrganizationSideMenuComponent() {
        return organizationSideMenuComponent;
    }
}
