package com.gsl.pages.pagecomponent.Admin.Organization;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Admin.Organization.OrganizationMenuType;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class OrganizationSideMenuComponent {

    private static final String ORGANIZATIONSIDEMENU = "//span[text()='%s']";

    public void clickMenuItem(OrganizationMenuType menuType) {
        String xpath = String.format(ORGANIZATIONSIDEMENU, menuType.getName());
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, menuType.getName());
    }
}
