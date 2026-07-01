package com.gsl.pages.pagecomponent.Finance.Setup;

import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class SetupSideMenuComponent {

    private static final String SIDE_MENU_ITEM = "//span[text()='%s']";

    public <T> T clickMenuItem(String name, Class<T> expectedPage) {
        By itemLocator = By.xpath(String.format(SIDE_MENU_ITEM, name));
        SeleniumUtils.click(itemLocator, WaitType.CLICKABLE, name);
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }
}
