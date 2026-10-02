package com.gsl.pages.pagecomponent.Finance;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.LogCategory;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.pages.enums.Finance.MainMenuType;
import org.openqa.selenium.By;

/**
 * Drives the Finance module's top-menu tab strip (e.g. Operations/Setup — see {@link
 * MainMenuType}). Held by {@link FinanceHomePage} and used by navigation helpers (e.g. {@code
 * NavigationUtils.navigateToFinanceSideMenu}) to switch tabs before drilling into a
 * module-specific side menu (e.g. {@link com.gsl.pages.pagecomponent.Finance.Operations.OperationsSideMenuComponent}).
 */
public class FinanceTopMenuComponent {

    private static final String MENU_ITEM = "//span[text()='%s']/parent::a";
    private static final By LBL_SELECTED_MENU = By.xpath("//span[@class='active']");

    /** @return the currently active top-menu tab's literal display text, e.g. "Operations". */
    public String getSelectedMenu() {
        return SeleniumUtils.getText(LBL_SELECTED_MENU, "Selected Menu", LogCategory.MENU);
    }

    /**
     * Clicks {@code requiredSubMenu}'s tab (skipping the click if it's already selected), waits
     * for the loading mask to clear, then instantiates {@code expectedPage} via its no-arg
     * constructor as the page object now on screen.
     *
     * @throws RuntimeException if {@code expectedPage} has no accessible no-arg constructor or it throws
     */
    public <T> T navigateToSubMenu(MainMenuType requiredSubMenu, Class<T> expectedPage) {
        String currentSubMenu = getSelectedMenu();

        if (!currentSubMenu.equalsIgnoreCase(requiredSubMenu.getName())) {
            String xpath = String.format(MENU_ITEM, requiredSubMenu.getName());
            SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, requiredSubMenu.getName(), LogCategory.MENU);
        }
        // Always wait for the loading mask to disappear — see TopModuleMenuComponent.navigateToModule
        // for why this can't stay inside the "already there" branch above.
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");

        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }
}