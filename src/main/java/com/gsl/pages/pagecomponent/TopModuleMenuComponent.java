package com.gsl.pages.pagecomponent;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.pagecomponent.Admin.AdminHomePage;
import com.gsl.pages.pagecomponent.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.pages.pagecomponent.POSManagement.POSManagementHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementHomePage;
import com.gsl.pages.pagecomponent.Report.ReportHomePage;
import com.gsl.pages.pagecomponent.SalesDistribution.SalesDistributionHomePage;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class TopModuleMenuComponent {

    private static final String MENU_ITEM = "//a[text()='%s']";

    /**
     * Determines the currently active module by parsing the browser's current URL.
     * @return The name of the active module (e.g., "Admin", "Inventory").
     */
    public String getActiveModuleName() {
        String currentUrl = DriverManager.getDriver().getCurrentUrl().toLowerCase();
        return parseModuleFromUrl(currentUrl);
    }

    /**
     * Intelligently navigates to the desired module.
     * It first checks if the module is already active by checking the URL. If not, it clicks on it.
     * @param requiredModule The module you want to navigate to.
     * @return The page object for the required module.
     */
    public <T> T navigateToModule(MainModuleType requiredModule) {
        String currentModule = getActiveModuleName();
        if (!currentModule.equalsIgnoreCase(requiredModule.getName())) {
            String xpath = String.format(MENU_ITEM, requiredModule.getName());
            SeleniumUtils.jsClick(By.xpath(xpath), requiredModule.getName());
            // Wait for the loading mask to disappear after the click
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        }
        return getPageObjectForModule(requiredModule);
    }

    /**
     * Returns an instance of the correct page object for a given module.
     * @param moduleType The module to get the page object for.
     * @return The corresponding page object.
     */
    @SuppressWarnings("unchecked")
    private <T> T getPageObjectForModule(MainModuleType moduleType) {
        switch (moduleType) {
            case ADMIN:
                return (T) new AdminHomePage();
            case INVENTORY:
                return (T) new InventoryHomePage();
            case PROCUREMENT:
                return (T) new ProcurementHomePage();
            case FINANCE:
                return (T) new FinanceHomePage();
            case SALESDISTRUBTION:
                return (T) new SalesDistributionHomePage();
            case POSMANAGEMENT:
                return (T) new POSManagementHomePage();
            case REPORT:
                return (T) new ReportHomePage();
            default:
                throw new IllegalArgumentException("Page object for " + moduleType.getName() + " is not defined.");
        }
    }

    /**
     * Parses the module name from a given URL.
     * @param url The URL to parse.
     * @return The name of the module found in the URL.
     */
    private String parseModuleFromUrl(String url) {
        if (url.contains("admin")) return "Admin";
        if (url.contains("inventory")) return "Inventory";
        if (url.contains("procurement")) return "Procurement";
        if (url.contains("finance")) return "Finance";
        if (url.contains("sales")) return "Sales & Distribution"; // Matching the enum
        if (url.contains("pos")) return "POS Management"; // Matching the enum
        if (url.contains("report")) return "Report";
        return "Unknown"; // Fallback
    }
}

