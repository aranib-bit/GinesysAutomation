package com.gsl.utils;

import com.gsl.constants.FrameworkConstants;
import com.gsl.pages.HomePage;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.pagecomponent.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Finance.Operations.OperationsHomePage;
import com.gsl.pages.pagecomponent.Finance.Setup.SetupHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementHomePage;
import com.gsl.utils.Selenium.SeleniumUtils;

/**
 * A utility class for common, high-level navigation sequences across the application.
 */
public final class NavigationUtils {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private NavigationUtils() {}

    /**
     * Navigates from the application's home page to a specific side menu item within the Inventory module.
     * This method handles the entire flow of selecting the module, the sub-menu, and the final side menu item.
     *
     * @param homePage The HomePage object obtained after logging in.
     * @param subMenu The sub-menu to navigate to within Inventory (e.g., PRODUCT_DEFINITION).
     * @param sideMenu The final side menu item to click on (e.g., GROUP).
     */
    public static <T> T navigateToInventorySideMenu(HomePage homePage, MainMenuType subMenu, Product_DefintionMenuType sideMenu, Class<T> expectedPage) {
        // 1. Navigate to the Inventory module (intelligently)
        InventoryHomePage inventoryHomePage = homePage.getTopModuleMenuComponent()
                .navigateToModule(MainModuleType.INVENTORY);

        // 2. Wait for the module to fully load before checking the active sub-menu state
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");

        // 3. Navigate to the desired sub-menu, which now returns the correct page object
        ProductDefinitionHomePage productDefinitionHomePage = inventoryHomePage.getInventoryTopMenuComponent()
                .navigateToSubMenu(subMenu, ProductDefinitionHomePage.class);

        // 4. Click the final side menu item (e.g., Group)
        T page = productDefinitionHomePage.getProductDefinitionSideMenuComponent()
                .clickMenuItem(sideMenu, expectedPage);

        // 5. Wait for the page loader to disappear
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        return page;
    }

    /**
     * Navigates from the application's home page to a specific side menu item within the Procurement module.
     * This method handles the entire flow of selecting the module, the sub-menu, and the final side menu item.
     *
     * @param homePage The HomePage object obtained after logging in.
     * @param subMenu The sub-menu to navigate to within Procurement (e.g., OPERATIONS).
     * @param sideMenu The final side menu item to click on (e.g., RECEIPTS).
     */
    public static <T> T navigateToProcurementSideMenu(HomePage homePage, com.gsl.pages.enums.Procurement.MainMenuType subMenu, com.gsl.pages.enums.Procurement.Operations.OperationsMenuType sideMenu, Class<T> expectedPage) {
        // 1. Navigate to the Procurement module
        ProcurementHomePage procurementHomePage = homePage.getTopModuleMenuComponent()
                .navigateToModule(MainModuleType.PROCUREMENT);

        // 2. Wait for the module to fully load before checking the active sub-menu state
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");

        // 3. Navigate to the desired sub-menu, which returns the correct page object
        com.gsl.pages.pagecomponent.Procurement.Operations.OperationsHomePage operationsHomePage =
                procurementHomePage.getProcurementTopMenuComponent()
                        .navigateToSubMenu(subMenu, com.gsl.pages.pagecomponent.Procurement.Operations.OperationsHomePage.class);

        // 4. Click the final side menu item — scoped to Procurement container
        T page = operationsHomePage.getOperationsSideMenuComponent()
                .clickMenuItem(sideMenu.getName(), expectedPage);

        // 5. Wait for the page loader to disappear
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        return page;
    }

    /**
     * Navigates from the application's home page to a specific side menu item within the Finance module.
     * This method handles the entire flow of selecting the module, the sub-menu, and the final side menu item.
     *
     * @param homePage The HomePage object obtained after logging in.
     * @param subMenu The sub-menu to navigate to within Finance (e.g., OPERATIONS).
     * @param sideMenu The final side menu item to click on (e.g., ARVOUCHER).
     */
    public static <T> T navigateToFinanceSideMenu(HomePage homePage, com.gsl.pages.enums.Finance.MainMenuType subMenu, com.gsl.pages.enums.Finance.Operations.OperationsMenuType sideMenu, Class<T> expectedPage) {
        // 1. Navigate to the Finance module
        FinanceHomePage financeHomePage = homePage.getTopModuleMenuComponent()
                .navigateToModule(MainModuleType.FINANCE);

        // 3. Navigate to the desired sub-menu, which returns the correct page object
        OperationsHomePage operationsHomePage = financeHomePage.getFinanceTopMenuComponent()
                .navigateToSubMenu(subMenu, OperationsHomePage.class);

        // 5. Click the final side menu item — scoped to Finance container only
        T page = operationsHomePage.getOperationsSideMenuComponent()
                .clickMenuItem(sideMenu.getName(), expectedPage);

        // 6. Wait for the page loader to disappear after navigation
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        return page;
    }


    /**
     * Navigates from the application's home page to a specific side menu item within Finance Setup.
     */
    public static <T> T navigateToFinanceSetupSideMenu(
            HomePage homePage,
            com.gsl.pages.enums.Finance.MainMenuType subMenu,
            com.gsl.pages.enums.Finance.Setup.SetupMenuType sideMenu,
            Class<T> expectedPage) {

        FinanceHomePage financeHomePage = homePage.getTopModuleMenuComponent()
                .navigateToModule(MainModuleType.FINANCE);

        SetupHomePage setupHomePage = financeHomePage.getFinanceTopMenuComponent()
                .navigateToSubMenu(subMenu, SetupHomePage.class);

        T page = setupHomePage.getSetupSideMenuComponent()
                .clickMenuItem(sideMenu.getName(), expectedPage);

        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        return page;
    }

    // You can add other common navigation workflows here as needed.
    /*
    public static void navigateToAdminUserManagement(HomePage homePage) {
        // ... navigation logic for admin user management
    }
    */
}
