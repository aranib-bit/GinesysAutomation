package com.pages;

import com.driver.DriverManager;
import com.pages.pagecomponent.SideMenuComponent;

public class HomePage {

    private SideMenuComponent sideMenuComponent;

    public String getHomePageTitle() {
        return DriverManager.getDriver().getTitle();
    }

    public HomePage() {
        sideMenuComponent = new SideMenuComponent();
    }

    public void clickOnAdmin() {
        sideMenuComponent.clickAdmin();
    }
}
