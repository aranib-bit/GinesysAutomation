package com.gsl.pages.pagecomponent.Inventory.Operations;

import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionSideMenuComponent;

public class OperationsHomePage {
    private OperationsSideMenuComponent operationsSideMenuComponent;

    public OperationsHomePage() {
        this.operationsSideMenuComponent=new OperationsSideMenuComponent();
    }

    public OperationsSideMenuComponent getOperationsSideMenuComponent() {
        return operationsSideMenuComponent;
    }
}
