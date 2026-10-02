package com.gsl.constants;

import org.openqa.selenium.By;

/**
 * Root-container locators for each top-level application module, scoping side-menu component
 * lookups to the module currently on screen instead of searching the whole DOM. Passed into a
 * module's side-menu component constructor (e.g. {@code OperationsSideMenuComponent}) so its
 * element lookups only ever match nodes inside that module's own container.
 */
public final class ModuleContainers {

    private ModuleContainers() {
    }

    /** Root container of the Finance module (element whose {@code id} contains {@code "finance"}). */
    public static final By FINANCE_ROOT = By.xpath("//*[contains(@id, 'finance')]");
    /** Root container of the Procurement module (element whose {@code id} contains {@code "procurement"}). */
    public static final By PROCUREMENT_ROOT = By.xpath("//*[contains(@id, 'procurement')]");
    /** Root container of the Inventory module (element whose {@code id} contains {@code "inventory"}). */
    public static final By INVENTORY_ROOT = By.xpath("//*[contains(@id, 'inventory')]");
}
