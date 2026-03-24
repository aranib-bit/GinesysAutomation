package com.gsl.pages.pagecomponent.Procurement.Operations;

import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OperationsSideMenuComponent {

    private static final String SIDE_MENU_ITEM = ".//span[text()='%s']";

    private final By moduleRoot;

    public OperationsSideMenuComponent(By moduleRoot) {
        this.moduleRoot = moduleRoot;
    }

    public <T> T clickMenuItem(String name, Class<T> expectedPage) {
        String relativeXpath = String.format(SIDE_MENU_ITEM, name);
        By itemLocator = By.xpath(relativeXpath);
        SeleniumUtils.click(
                SeleniumUtils.findChildElement(moduleRoot, itemLocator),
                WaitType.CLICKABLE,
                name
        );
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }
}
