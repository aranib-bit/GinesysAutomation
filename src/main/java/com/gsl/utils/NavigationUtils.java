package com.gsl.utils;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.driver.ExecutionContext;
import com.gsl.driver.ExecutionManager;
import com.gsl.pages.HomePage;
import com.gsl.pages.VerifiablePage;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.pages.pagecomponent.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Finance.FinanceTopMenuComponent;
import com.gsl.pages.pagecomponent.Finance.Operations.OperationsHomePage;
import com.gsl.pages.pagecomponent.Finance.Setup.SetupHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryHomePage;
import com.gsl.pages.pagecomponent.Inventory.InventoryTopMenuComponent;
import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementHomePage;
import com.gsl.pages.pagecomponent.Procurement.ProcurementTopMenuComponent;
import com.gsl.reports.NavigationLogger;
import com.gsl.reports.PoolAttributionStats;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.function.Supplier;

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
        ExecutionContext ctx = ExecutionContext.current();
        long navStart = System.currentTimeMillis();
        ProductDefinitionHomePage productDefinitionHomePage;

        if (isCachedSubMenuStillValid(ctx, "INVENTORY", "Inventory", subMenu.name(),
                () -> homePage.getTopModuleMenuComponent().getActiveModuleName(),
                () -> new InventoryTopMenuComponent().getSelectedMenu())
                && ctx.getCurrentSubModulePage() instanceof ProductDefinitionHomePage cached) {
            NavigationLogger.alreadyLoaded("Inventory");
            NavigationLogger.alreadyLoaded(subMenu.name());
            productDefinitionHomePage = cached;
        } else {
            // 1. Navigate to the Inventory module (intelligently)
            long moduleStart = System.currentTimeMillis();
            InventoryHomePage inventoryHomePage = homePage.getTopModuleMenuComponent()
                    .navigateToModule(MainModuleType.INVENTORY);
            NavigationLogger.navigated("Inventory", System.currentTimeMillis() - moduleStart);
            ctx.setModuleCheckpoint("INVENTORY");

            // 2. Wait for the module to fully load before checking the active sub-menu state
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");

            // 3. Navigate to the desired sub-menu, which now returns the correct page object
            long subMenuStart = System.currentTimeMillis();
            productDefinitionHomePage = inventoryHomePage.getInventoryTopMenuComponent()
                    .navigateToSubMenu(subMenu, ProductDefinitionHomePage.class);
            NavigationLogger.navigated(subMenu.name(), System.currentTimeMillis() - subMenuStart);
            ctx.setSubModuleCheckpoint(subMenu.name(), productDefinitionHomePage);
        }

        // Always clear a leftover mask before the next click, even on the cache-hit path above —
        // otherwise session reuse has no clearance point between "already loaded" and this click.
        waitForSideMenuClickable();

        // 4. Click the final side menu item (e.g., Group)
        T page = productDefinitionHomePage.getProductDefinitionSideMenuComponent()
                .clickMenuItem(sideMenu, expectedPage);

        // 5. Wait for the page loader to disappear BEFORE verifying the new page — ExtJS is still
        // rendering the side-menu transition immediately after the click; verifying too early is
        // what causes a page-header visibility timeout (the header wait itself has no ExtJS-settle
        // gate of its own).
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        verifyAndRecordPage(ctx, page, sideMenu.name());
        ExecutionManager.logState("navigation:Inventory/" + subMenu.name() + "/" + sideMenu.name());
        finishColdStartIfInProgress(ctx, navStart);
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
        ExecutionContext ctx = ExecutionContext.current();
        long navStart = System.currentTimeMillis();
        com.gsl.pages.pagecomponent.Procurement.Operations.OperationsHomePage operationsHomePage;

        if (isCachedSubMenuStillValid(ctx, "PROCUREMENT", "Procurement", subMenu.name(),
                () -> homePage.getTopModuleMenuComponent().getActiveModuleName(),
                () -> new ProcurementTopMenuComponent().getSelectedMenu())
                && ctx.getCurrentSubModulePage() instanceof com.gsl.pages.pagecomponent.Procurement.Operations.OperationsHomePage cached) {
            NavigationLogger.alreadyLoaded("Procurement");
            NavigationLogger.alreadyLoaded(subMenu.name());
            operationsHomePage = cached;
        } else {
            // 1. Navigate to the Procurement module
            long moduleStart = System.currentTimeMillis();
            ProcurementHomePage procurementHomePage = homePage.getTopModuleMenuComponent()
                    .navigateToModule(MainModuleType.PROCUREMENT);
            NavigationLogger.navigated("Procurement", System.currentTimeMillis() - moduleStart);
            ctx.setModuleCheckpoint("PROCUREMENT");

            // 2. Wait for the module to fully load before checking the active sub-menu state
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");

            // 3. Navigate to the desired sub-menu, which returns the correct page object
            long subMenuStart = System.currentTimeMillis();
            operationsHomePage = procurementHomePage.getProcurementTopMenuComponent()
                    .navigateToSubMenu(subMenu, com.gsl.pages.pagecomponent.Procurement.Operations.OperationsHomePage.class);
            NavigationLogger.navigated(subMenu.name(), System.currentTimeMillis() - subMenuStart);
            ctx.setSubModuleCheckpoint(subMenu.name(), operationsHomePage);
        }

        // Always clear a leftover mask before the next click, even on the cache-hit path above —
        // otherwise session reuse has no clearance point between "already loaded" and this click.
        waitForSideMenuClickable();

        // 4. Click the final side menu item — scoped to Procurement container
        T page = operationsHomePage.getOperationsSideMenuComponent()
                .clickMenuItem(sideMenu, expectedPage);

        // 5. Wait for the page loader to disappear BEFORE verifying the new page — ExtJS is still
        // rendering the side-menu transition immediately after the click; verifying too early is
        // what causes a page-header visibility timeout (the header wait itself has no ExtJS-settle
        // gate of its own).
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        verifyAndRecordPage(ctx, page, sideMenu.getName());
        ExecutionManager.logState("navigation:Procurement/" + subMenu.name() + "/" + sideMenu.getName());
        finishColdStartIfInProgress(ctx, navStart);
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
        ExecutionContext ctx = ExecutionContext.current();
        long navStart = System.currentTimeMillis();
        OperationsHomePage operationsHomePage;

        if (isCachedSubMenuStillValid(ctx, "FINANCE", "Finance", subMenu.name(),
                () -> homePage.getTopModuleMenuComponent().getActiveModuleName(),
                () -> new FinanceTopMenuComponent().getSelectedMenu())
                && ctx.getCurrentSubModulePage() instanceof OperationsHomePage cached) {
            NavigationLogger.alreadyLoaded("Finance");
            NavigationLogger.alreadyLoaded(subMenu.name());
            operationsHomePage = cached;
        } else {
            // 1. Navigate to the Finance module
            long moduleStart = System.currentTimeMillis();
            FinanceHomePage financeHomePage = homePage.getTopModuleMenuComponent()
                    .navigateToModule(MainModuleType.FINANCE);
            NavigationLogger.navigated("Finance", System.currentTimeMillis() - moduleStart);
            ctx.setModuleCheckpoint("FINANCE");

            // 3. Navigate to the desired sub-menu, which returns the correct page object
            long subMenuStart = System.currentTimeMillis();
            operationsHomePage = financeHomePage.getFinanceTopMenuComponent()
                    .navigateToSubMenu(subMenu, OperationsHomePage.class);
            NavigationLogger.navigated(subMenu.name(), System.currentTimeMillis() - subMenuStart);
            ctx.setSubModuleCheckpoint(subMenu.name(), operationsHomePage);
        }

        // Always clear a leftover mask before the next click, even on the cache-hit path above —
        // otherwise session reuse has no clearance point between "already loaded" and this click.
        waitForSideMenuClickable();

        // 5. Click the final side menu item — scoped to Finance container only
        T page = operationsHomePage.getOperationsSideMenuComponent()
                .clickMenuItem(sideMenu, expectedPage);

        // 6. Wait for the page loader to disappear BEFORE verifying the new page — ExtJS is still
        // rendering the side-menu transition immediately after the click; verifying too early is
        // exactly what caused the 30s pageheader-toolbar visibility timeout seen on
        // GeneralVoucherListPage (the header wait itself has no ExtJS-settle gate of its own).
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        verifyAndRecordPage(ctx, page, sideMenu.getName());
        ExecutionManager.logState("navigation:Finance/" + subMenu.name() + "/" + sideMenu.getName());
        finishColdStartIfInProgress(ctx, navStart);
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

        ExecutionContext ctx = ExecutionContext.current();
        long navStart = System.currentTimeMillis();
        SetupHomePage setupHomePage;

        if (isCachedSubMenuStillValid(ctx, "FINANCE", "Finance", subMenu.name(),
                () -> homePage.getTopModuleMenuComponent().getActiveModuleName(),
                () -> new FinanceTopMenuComponent().getSelectedMenu())
                && ctx.getCurrentSubModulePage() instanceof SetupHomePage cached) {
            NavigationLogger.alreadyLoaded("Finance");
            NavigationLogger.alreadyLoaded(subMenu.name());
            setupHomePage = cached;
        } else {
            long moduleStart = System.currentTimeMillis();
            FinanceHomePage financeHomePage = homePage.getTopModuleMenuComponent()
                    .navigateToModule(MainModuleType.FINANCE);
            NavigationLogger.navigated("Finance", System.currentTimeMillis() - moduleStart);
            ctx.setModuleCheckpoint("FINANCE");

            long subMenuStart = System.currentTimeMillis();
            setupHomePage = financeHomePage.getFinanceTopMenuComponent()
                    .navigateToSubMenu(subMenu, SetupHomePage.class);
            NavigationLogger.navigated(subMenu.name(), System.currentTimeMillis() - subMenuStart);
            ctx.setSubModuleCheckpoint(subMenu.name(), setupHomePage);
        }

        // Always clear a leftover mask before the next click, even on the cache-hit path above —
        // otherwise session reuse has no clearance point between "already loaded" and this click.
        waitForSideMenuClickable();

        T page = setupHomePage.getSetupSideMenuComponent()
                .clickMenuItem(sideMenu, expectedPage);

        // Wait for the page loader to disappear BEFORE verifying the new page — ExtJS is still
        // rendering the side-menu transition immediately after the click; verifying too early is
        // what causes a page-header visibility timeout (the header wait itself has no ExtJS-settle
        // gate of its own).
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        verifyAndRecordPage(ctx, page, sideMenu.getName());
        ExecutionManager.logState("navigation:FinanceSetup/" + subMenu.name() + "/" + sideMenu.getName());
        finishColdStartIfInProgress(ctx, navStart);
        return page;
    }

    /**
     * Clears both loading-overlay flavors that can still be blocking the side-menu tree node
     * about to be clicked. {@code getLoadingMask()} (.x-mask-fixed) is the routine ExtJS loading
     * mask; {@code getSplashScreen()} (#splashScreen) is the app-level splash screen, which
     * disappears via a CSS opacity fade rather than being removed/hidden outright. Nothing waited
     * on the latter before the side-menu click, which produced 3 hard failures in report
     * 20260901_112001 (editGeneralVoucherHeader x1, deleteGeneralVoucher x2) — ChromeDriver
     * reported {@code <div id="splashScreen" style="opacity: 0.0296854;">} intercepting the click
     * right after the preceding module navigation, mid fade-out, and it was misreported as "part
     * of the page layout" (see {@code SeleniumUtils.detectInterceptingOverlay} for the matching
     * defensive-backstop fix). {@code elementDisappear} already uses the "genuinely visible"
     * opacity-aware check (see {@code WaitUtils.isGenuinelyVisible}), so it correctly waits out
     * the fade instead of treating near-zero-but-nonzero opacity as already gone.
     *
     * <p>Single choke point for all four {@code navigateTo*SideMenu} flows — {@code
     * clickMenuItem} is not a shared method (Finance/Inventory/Procurement/FinanceSetup each have
     * their own, independent side-menu component), so this is the one place all four flows
     * actually share.
     */
    private static void waitForSideMenuClickable() {
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        // Round 14 (§27c): was a bare, single-attempt 30s elementDisappear on the same
        // #splashScreen element WaitUtils.waitForSplashScreenClear() already handles properly
        // (staged, logged) for waitForClickOverlay() — now calling that proven implementation
        // directly instead of a weaker duplicate.
        WaitUtils.waitForSplashScreenClear();
    }

    /**
     * Defense-in-depth for the module/sub-menu cache: {@link ExecutionContext}'s
     * {@code currentModule}/{@code currentSubModule} flags are only as trustworthy as whatever
     * cleared them on logout. Before trusting a cache hit, re-verify against the live DOM (the
     * same ground truth {@code navigateToModule}/{@code navigateToSubMenu} already use internally)
     * so a stale flag — from any future cause, not just a known one — falls through to real
     * navigation instead of a blind click on a menu item that was never opened this session.
     *
     * Generalized across modules: each module's top-menu component (Finance/Inventory/Procurement)
     * has its own {@code getSelectedMenu()} with the same shape, so the live-recheck logic is
     * shared here instead of duplicated per module.
     */
    private static boolean isCachedSubMenuStillValid(
            ExecutionContext ctx, String moduleKey, String moduleDisplayName, String subMenuName,
            Supplier<String> liveActiveModuleName, Supplier<String> liveSelectedSubMenu) {
        if (!(moduleKey.equals(ctx.getCurrentModule()) && subMenuName.equals(ctx.getCurrentSubModule()))) {
            return false;
        }
        try {
            String activeModule = liveActiveModuleName.get();
            String activeSubMenu = liveSelectedSubMenu.get();
            if (moduleDisplayName.equalsIgnoreCase(activeModule) && subMenuName.equalsIgnoreCase(activeSubMenu)) {
                return true;
            }
            NavigationLogger.warn("ExecutionContext cache claimed {}/{} already loaded, but live DOM "
                            + "shows module='{}' submenu='{}' — forcing fresh navigation.",
                    moduleDisplayName, subMenuName, activeModule, activeSubMenu);
            return false;
        } catch (Exception e) {
            NavigationLogger.warn("Live navigation cache-check threw ({}) for {}/{} — forcing fresh navigation.",
                    e.getMessage(), moduleDisplayName, subMenuName);
            return false;
        }
    }

    /**
     * After the final side-menu click lands on a page, verify page identity if the page object
     * supports it ({@link VerifiablePage}) and, only once verified, record the finest-grained
     * checkpoint ({@code ExecutionContext.setCurrentPage}) — the checkpoint is only ever set once
     * independently confirmed, never assumed. Pages that don't implement {@link VerifiablePage} yet
     * simply skip both steps (no regression versus today's behavior).
     */
    private static void verifyAndRecordPage(ExecutionContext ctx, Object page, String pageLabel) {
        if (page instanceof VerifiablePage verifiable) {
            try {
                verifiable.verifyOnThisPage();
            } catch (RuntimeException e) {
                logVerificationFailureDiagnostics(pageLabel, e);
                throw e;
            }
            ctx.setCurrentPage(pageLabel);
        }
    }

    /**
     * Captures the live DOM/mask/element-count state at the moment a page-identity verification
     * fails — the failure-branch counterpart {@link ExecutionManager#logState} never covers (it
     * only runs after verification succeeds). Without this, a "page-header never appeared after
     * a side-menu click" timeout was undiagnosable after the fact: no record of whether a genuine
     * ExtJS mask was actually blocking, whether the target menu-item locator matched more than
     * one element (a stale/duplicate DOM node — ExtJS is known to pool rather than destroy
     * elements, per the {@code WaitUtils.isGenuineMaskVisible} investigation), or what the live
     * URL was at that instant. Reconstructs the same {@code //span[text()='<pageLabel>']} locator
     * {@code OperationsSideMenuComponent.clickMenuItem} used to click the menu item, so a count
     * &gt; 1 here directly confirms the "stale duplicate span" theory for next time.
     *
     * <p>Purely additive diagnostics — never throws itself, and always rethrows the original
     * failure unchanged so no test's behavior changes.
     *
     * <p>Round 11 (§21): made {@code public} so {@code OperationsSideMenuComponent.clickMenuItem}
     * can reuse it directly on its own retry-exhaustion path — that failure previously propagated
     * straight out of {@code clickMenuItem} without ever reaching {@link #verifyAndRecordPage},
     * the only place this used to be wired in, leaving that failure mode with no diagnostic
     * capture at all.
     */
    public static void logVerificationFailureDiagnostics(String pageLabel, RuntimeException failure) {
        try {
            WebDriver driver = DriverManager.getDriver();
            boolean maskVisible = WaitUtils.isExtMaskPresent(driver);
            By menuItemLocator = By.xpath(String.format("//span[text()='%s']", pageLabel));
            int matchingElements = driver.findElements(menuItemLocator).size();
            String currentUrl = driver.getCurrentUrl();
            NavigationLogger.warn(
                    "Page-identity verification failed for '{}' — LoaderVisible={} | "
                            + "MenuItemMatches({})={} | CurrentUrl={} | Failure={}: {}",
                    pageLabel, maskVisible, menuItemLocator, matchingElements, currentUrl,
                    failure.getClass().getSimpleName(), failure.getMessage());
        } catch (Exception diagnosticFailure) {
            NavigationLogger.warn("Page-identity verification failed for '{}', and diagnostic "
                            + "capture itself threw ({}) — session may already be dead.",
                    pageLabel, diagnosticFailure.getMessage());
        }
    }

    /**
     * If this thread's cold-start window is still open (set by {@code WebTest.setUp()} the moment
     * it detected this thread's very first-ever session), this navigation call is guaranteed to be
     * the one real (non-cache-hit) navigation that completes it — a genuinely cold thread can never
     * satisfy {@code isCachedSubMenuStillValid} since {@code ExecutionContext} starts with no
     * module/sub-menu recorded. Records this navigation's elapsed time into the same Suite/Class
     * Setup bucket the login step already contributed to, then closes the window — every
     * subsequent row on this thread goes through normal reuse/lease accounting with no special
     * attribution needed.
     */
    private static void finishColdStartIfInProgress(ExecutionContext ctx, long navStart) {
        if (ctx.isColdStartInProgress()) {
            PoolAttributionStats.recordColdStartWarmup(ctx.getColdStartLabel(), System.currentTimeMillis() - navStart);
            ctx.setColdStartInProgress(false);
        }
    }

    // You can add other common navigation workflows here as needed.
    /*
    public static void navigateToAdminUserManagement(HomePage homePage) {
        // ... navigation logic for admin user management
    }
    */
}
