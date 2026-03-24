package com.gsl.pages.pagecomponent.Finance;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.pages.enums.Finance.MainMenuType;
import org.openqa.selenium.By;

public class FinanceTopMenuComponent {

    private static final String MENU_ITEM = "//span[text()='%s']/parent::a";
    private static final By LBL_SELECTED_MENU = By.xpath("//span[@class='active']");

    public String getSelectedMenu() {
        return SeleniumUtils.getText(LBL_SELECTED_MENU, "Selected Menu");
    }

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