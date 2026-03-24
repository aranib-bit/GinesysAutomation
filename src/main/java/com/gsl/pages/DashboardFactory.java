package com.gsl.pages;

import com.gsl.driver.DriverManager;
import com.gsl.pages.pagecomponent.Admin.AdminHomePage;
import com.gsl.pages.pagecomponent.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.pages.pagecomponent.POSManagement.POSManagementHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementHomePage;
import com.gsl.pages.pagecomponent.Report.ReportHomePage;
import com.gsl.pages.pagecomponent.SalesDistribution.SalesDistributionHomePage;


public class DashboardFactory {

    /**
     * Determines and returns the appropriate dashboard page object based on the current URL.
     *
     * @return An instance of the correct dashboard page object.
     */
    public static Object getDashboardPage() {
        String currentUrl = DriverManager.getDriver().getCurrentUrl().toLowerCase();

        if (currentUrl.contains("admin")) {
            return new AdminHomePage();
        } else if (currentUrl.contains("inventory")) {
            return new InventoryHomePage();
        } else if (currentUrl.contains("procurement")) {
            return new ProcurementHomePage();
        } else if (currentUrl.contains("finance")) {
            return new FinanceHomePage();
        } else if (currentUrl.contains("salesndistribution")) {
            return new SalesDistributionHomePage();
        } else if (currentUrl.contains("retailmanage")) {
            return new POSManagementHomePage();
        } else if (currentUrl.contains("report")) {
            return new ReportHomePage();
        }
        else {
            // Fallback or error
            throw new IllegalStateException("Could not determine the dashboard page from URL: " + currentUrl);
        }
    }
}
