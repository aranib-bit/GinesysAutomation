package com.gsl.pages.pagecomponent.Inventory.WMS;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.WMS.WMSMenuType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class WMSSideMenuComponent {

    private static final String MENU_ITEM = "//span[text()='%s']"; // Assuming a similar structure for side menus

    /**
     * Clicks on a menu item in the WMS side menu.
     * @param menuType The menu item to click on.
     */
    public void clickMenuItem(WMSMenuType menuType) {
        String xpath = String.format(MENU_ITEM, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName());
    }
}
