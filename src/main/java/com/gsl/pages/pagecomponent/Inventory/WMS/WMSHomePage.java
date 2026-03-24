package com.gsl.pages.pagecomponent.Inventory.WMS;

import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionSideMenuComponent;

public class WMSHomePage {
    private WMSSideMenuComponent wmsSideMenuComponent;

    public WMSHomePage() {
        this.wmsSideMenuComponent=new WMSSideMenuComponent();
    }

    public WMSSideMenuComponent getWmsSideMenuComponent() {
        return wmsSideMenuComponent;
    }
}
