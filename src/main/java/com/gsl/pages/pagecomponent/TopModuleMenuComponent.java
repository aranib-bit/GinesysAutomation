package com.gsl.pages.pagecomponent;

import com.gsl.config.ConfigFactory;
import com.gsl.config.WaitConfig;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.driver.RecoveryManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.ReportSeverity;
import com.gsl.enums.WaitType;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.topmenucomponent.DashboardTitle;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.pagecomponent.Admin.AdminHomePage;
import com.gsl.pages.pagecomponent.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.pages.pagecomponent.POSManagement.POSManagementHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementHomePage;
import com.gsl.pages.pagecomponent.Report.ReportHomePage;
import com.gsl.pages.pagecomponent.SalesDistribution.SalesDistributionHomePage;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

/**
 * Drives the application's top-level module switcher — the Admin/Inventory/Procurement/Finance/
 * Sales &amp; Distribution/POS Management/Report tabs in the app's own top nav bar — keyed by
 * {@link MainModuleType}. {@link #navigateToModule(MainModuleType)} is the entry point test code
 * should call to land on a given module's home page: it skips the click entirely when the URL
 * already shows the module as active (important when a session is reused across tests), always
 * clears the post-click "Loading Package..." bootstrap mask, and — for a module with a confirmed
 * dashboard header (see {@link DashboardTitle#forModule(MainModuleType)}) — verifies the dashboard
 * actually rendered, auto-recovering via a browser refresh on the known ExtJS rendering race.
 * <p>Returns a fresh instance of the target module's own home-page object (e.g. {@link
 * AdminHomePage}, {@link FinanceHomePage}) so callers can chain straight into that module's own
 * navigation.
 */
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
    @SuppressWarnings("unchecked")
    public <T> T navigateToModule(MainModuleType requiredModule) {
        String currentModule = getActiveModuleName();
        if (!currentModule.equalsIgnoreCase(requiredModule.getName())) {
            String xpath = String.format(MENU_ITEM, requiredModule.getName());
            SeleniumUtils.jsClick(By.xpath(xpath), requiredModule.getName(), LogCategory.MODULE);
        }
        // Always wait for the loading mask to disappear, even when the click above was skipped
        // because a reused session was already on this module — a mask left over from the prior
        // test's last action can still be up, and the caller's next click has no other guaranteed
        // clearance point before it fires.
        //
        // Uses module.packageload.timeout.seconds (60s default), not the generic 30s timeout —
        // this is the post-module-entry "Loading Package..." bootstrap wait (menu/permissions/
        // site package fetch), a heavier one-off step than a routine mask clear. Confirmed via
        // screenshot evidence (reports/runs/20260822_213931) that the spinner is still genuinely
        // active past 30s under staging load, not a stale/false-positive mask.
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask",
                ConfigFactory.getWaitConfig().modulePackageLoadTimeoutSeconds());

        Object page = getPageObjectForModule(requiredModule);
        verifyDashboardLoaded(requiredModule, page);
        return (T) page;
    }

    /**
     * Confirms the module's dashboard actually rendered (not just that the loading mask
     * cleared), and auto-recovers via a browser refresh if it didn't — a known ExtJS rendering
     * race whose accepted manual workaround is exactly this refresh. Only runs when a dashboard
     * title has been confirmed for this module (see {@link DashboardTitle}) and the check hasn't
     * been disabled via config. A recovered dashboard still counts as an overall PASS, but with
     * a prominent, screenshot-backed warning entry in the report; a dashboard that still won't
     * load after the refresh is a genuine, well-logged failure.
     */
    private void verifyDashboardLoaded(MainModuleType requiredModule, Object page) {
        if (!(page instanceof VerifiablePage verifiable)) return;
        if (DashboardTitle.forModule(requiredModule).isEmpty()) return;
        if (!isDashboardVerificationEnabled(requiredModule)) return;

        try {
            verifiable.verifyOnThisPage(); // first attempt
            ReportLogger.verification(requiredModule.getName() + " Module is loaded");
        } catch (RuntimeException firstFailure) {
            ExtentLogger.warningCategorizedWithScreenshot(LogCategory.MODULE, ReportSeverity.HIGH,
                    requiredModule.getName() + " Dashboard did not load properly on first attempt after "
                            + requiredModule.getName() + " menu click — " + firstFailure.getMessage());
            ExtentLogger.failCategorized(LogCategory.VERIFICATION, requiredModule.getName()
                    + " Module did not load on first attempt — attempting refresh-based recovery ("
                    + firstFailure.getMessage() + ")");

            boolean recovered = RecoveryManager.refreshAndRestore(requiredModule.getName() + " Dashboard", () -> {
                SeleniumUtils.elementDisappear(FrameworkConstants.getSplashScreen(), "SplashScreen",
                        ConfigFactory.getWaitConfig().modulePackageLoadTimeoutSeconds());
                SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask",
                        ConfigFactory.getWaitConfig().modulePackageLoadTimeoutSeconds());
                verifiable.verifyOnThisPage(); // re-verify post-refresh
            });

            if (recovered) {
                ExtentLogger.passCategorized(LogCategory.RECOVERY,
                        requiredModule.getName() + " Dashboard recovered after refresh (matches known manual workaround).");
                ReportLogger.verification(requiredModule.getName() + " Module is loaded (after refresh-based recovery)");
            } else {
                ExtentLogger.fail(requiredModule.getName()
                        + " Dashboard did not load even after refresh-based recovery.");
                ExtentLogger.failCategorized(LogCategory.VERIFICATION, requiredModule.getName()
                        + " Module failed to load even after refresh-based recovery");
                throw new IllegalStateException(requiredModule.getName()
                        + " Dashboard failed to load after menu click and after refresh-recovery — hard failure.");
            }
        }
    }

    /** Config gate: global switch (default enabled) AND not in the per-module disable list. */
    private boolean isDashboardVerificationEnabled(MainModuleType module) {
        WaitConfig cfg = ConfigFactory.getWaitConfig();
        if (!cfg.dashboardVerificationEnabled()) return false;
        String disabled = cfg.dashboardVerificationDisabledModules();
        if (disabled == null || disabled.isBlank()) return true;
        for (String name : disabled.split(",")) {
            if (name.trim().equalsIgnoreCase(module.getName())) return false;
        }
        return true;
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

