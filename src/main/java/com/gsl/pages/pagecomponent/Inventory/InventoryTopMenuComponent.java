package com.gsl.pages.pagecomponent.Inventory;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.MainMenuType;
import com.gsl.pages.pagecomponent.Inventory.Logistics.LogisticsHomePage;
import com.gsl.pages.pagecomponent.Inventory.Operations.OperationsHomePage;
import com.gsl.pages.pagecomponent.Inventory.Product_Definition.ProductDefinitionHomePage;
import com.gsl.pages.pagecomponent.Inventory.WMS.WMSHomePage;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class InventoryTopMenuComponent {
    private static final String MENU_ITEM = "//span[text()='%s']/parent::a";
    // Using a more precise XPath to find the span with the exact class 'active'
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
    public <T> T navigateToSubMenu(MainMenuType requiredSubMenu, Class<T> expectedPage) {
        String currentSubMenu = getSelectedMenu();
        if (!currentSubMenu.equalsIgnoreCase(requiredSubMenu.getName())) {
            String xpath = String.format(MENU_ITEM, requiredSubMenu.getName());
            SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, requiredSubMenu.getName());
            SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");
        }
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }
}
