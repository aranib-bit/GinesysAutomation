package com.gsl.pages.pagecomponent.Procurement.Setup;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Procurement.Setup.SetupMenuType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class SetupSideMenuComponent {

    private static final String SIDE_MENU_ITEM = "//span[text()='%s']";

    public <T> T clickMenuItem(SetupMenuType menuItem, Class<T> expectedPage) {
        String xpath = String.format(SIDE_MENU_ITEM, menuItem.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuItem.getName());
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }
}
