package com.gsl.constants;

import org.openqa.selenium.By;

public final class ModuleContainers {

    private ModuleContainers() {
    }

    public static final By FINANCE_ROOT = By.xpath("//*[contains(@id, 'finance')]");
    public static final By PROCUREMENT_ROOT = By.xpath("//*[contains(@id, 'procurement')]");
    public static final By INVENTORY_ROOT = By.xpath("//*[contains(@id, 'inventory')]");
}
