package com.gsl.pages.pagecomponent.Inventory.Operations;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.Product_Definition.Product_DefintionMenuType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OperationsSideMenuComponent {
    private static final String OPERATIONSSIDEMENU = "//span[text()='%s']";

    public void clickMenuItem(Product_DefintionMenuType menuType) {
        String xpath = String.format(OPERATIONSSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName());
    }
}
