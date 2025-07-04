package com.pages.pagecomponent;

import com.enums.WaitType;
import org.openqa.selenium.By;

import static com.utils.Selenium.SeleniumUtils.click;

public class SideMenuComponent {
    private static final By LNK_ADMIN = By.xpath("//a[span[text()='Admin']]");
    private String sideMenus = "//a[span[text()='%replaceable%']]";

    public void clickOnMenu(String menu) {
        String replace = sideMenus.replace("replaceable", "Admin");
        click(By.xpath(replace), menu);
    }

    public void clickAdmin() {
        click(LNK_ADMIN, WaitType.CLICKABLE, "Admin Module");
    }

}

