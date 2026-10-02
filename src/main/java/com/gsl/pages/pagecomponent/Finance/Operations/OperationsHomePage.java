package com.gsl.pages.pagecomponent.Finance.Operations;

/**
 * Page object for Finance &gt; Operations' landing page — reached after {@link
 * com.gsl.pages.pagecomponent.Finance.FinanceTopMenuComponent#navigateToSubMenu} selects the
 * "Operations" tab. Its only job is to expose the {@link OperationsSideMenuComponent} used to
 * drill into a specific voucher/journal side-menu item (see {@link
 * com.gsl.pages.enums.Finance.Operations.OperationsMenuType}).
 */
public class OperationsHomePage {

    private OperationsSideMenuComponent operationsSideMenuComponent;

    public OperationsHomePage() {
        this.operationsSideMenuComponent = new OperationsSideMenuComponent();
    }

    /** @return this page's {@link OperationsSideMenuComponent}, for clicking side-menu items. */
    public OperationsSideMenuComponent getOperationsSideMenuComponent() {
        return operationsSideMenuComponent;
    }
}
