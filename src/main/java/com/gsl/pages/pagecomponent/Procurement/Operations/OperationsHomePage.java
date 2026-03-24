package com.gsl.pages.pagecomponent.Procurement.Operations;

import com.gsl.constants.ModuleContainers;

public class OperationsHomePage {

    private OperationsSideMenuComponent operationsSideMenuComponent;

    public OperationsHomePage() {
        this.operationsSideMenuComponent = new OperationsSideMenuComponent(ModuleContainers.PROCUREMENT_ROOT);
    }

    public OperationsSideMenuComponent getOperationsSideMenuComponent() {
        return operationsSideMenuComponent;
    }
}
