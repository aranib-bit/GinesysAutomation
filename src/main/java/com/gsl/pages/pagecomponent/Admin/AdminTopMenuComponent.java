package com.gsl.pages.pagecomponent.Admin;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Admin.MainMenuType;
import com.gsl.pages.pagecomponent.Admin.Finance.FinanceHomePage;
import com.gsl.pages.pagecomponent.Admin.Organization.OrganizationHomePage;
import com.gsl.pages.pagecomponent.Admin.Security.SecurityHomePage;
import com.gsl.pages.pagecomponent.Admin.Utilities.UtilitiesHomePage;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class AdminTopMenuComponent {
    private static final String MENU_ITEM = "//span[text()='%s']/parent::a";
    private static final By LBL_SELECTED_MENU = By.xpath("//span[@class='active']");

    /**
     * Gets the name of the currently selected sub-menu.
     * @return The text of the selected menu item.
     */
    public String getSelectedMenu() {
        return SeleniumUtils.getText(LBL_SELECTED_MENU, "Selected Menu");
    }

    /**
     * Intelligently navigates to the desired sub-menu and returns its page object.
     * @param requiredSubMenu The sub-menu you want to ensure is active.
     * @return The corresponding page object for the sub-menu.
     */
    public <T> T navigateToSubMenu(MainMenuType requiredSubMenu) {
        String currentSubMenu = getSelectedMenu();
        if (!currentSubMenu.equalsIgnoreCase(requiredSubMenu.getName())) {
            String xpath = String.format(MENU_ITEM, requiredSubMenu.getName());
            SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, requiredSubMenu.getName());
        }
        return getPageObjectForSubMenu(requiredSubMenu);
    }

    /**
     * Returns an instance of the correct page object for a given sub-menu.
     * @param menuType The sub-menu to get the page object for.
     * @return The corresponding page object.
     */
    @SuppressWarnings("unchecked")
    private <T> T getPageObjectForSubMenu(MainMenuType menuType) {
        switch (menuType) {
            case ORGANIZATION:
                return (T) new OrganizationHomePage();
            case FINANCE:
                return (T) new FinanceHomePage();
            case SECURITY:
                return (T) new SecurityHomePage();
            case UTILITIES:
                return (T) new UtilitiesHomePage();
            default:
                throw new IllegalArgumentException("Page object for " + menuType.getName() + " is not defined.");
        }
    }
}
