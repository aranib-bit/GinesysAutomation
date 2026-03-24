package com.gsl.pages.pagecomponent.Admin;

import com.gsl.pages.pagecomponent.Admin.AdminTopMenuComponent;

public class AdminHomePage {

    private AdminTopMenuComponent adminTopMenuComponent;

    public AdminHomePage() {
        this.adminTopMenuComponent = new AdminTopMenuComponent();
    }

    public AdminTopMenuComponent getAdminTopMenuComponent() {
        return adminTopMenuComponent;
    }
}
