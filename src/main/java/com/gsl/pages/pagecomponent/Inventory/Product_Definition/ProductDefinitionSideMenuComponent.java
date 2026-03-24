package com.gsl.pages.pagecomponent.Inventory.Product_Definition;


import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ProductDefinitionSideMenuComponent {
    private static final String PRODUCTDEFINTIONSIDEMENU = "//span[text()='%s']";

    public <T> T clickMenuItem(Product_DefintionMenuType menuType, Class<T> expectedPage) {
        String xpath = String.format(PRODUCTDEFINTIONSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName());
        try {
            return expectedPage.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + expectedPage.getName(), e);
        }
    }

}