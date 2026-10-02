package com.gsl.pages.enums.Finance.Setup;

import org.openqa.selenium.By;

/**
 * Items on the Account Group tree page's right-click context menu — see
 * {@code AccountGroupListPage}'s {@code clickContextMenuItem(AccountGroupContextMenu)}, used by
 * both {@code CreateAccountGroup} (ADD) and {@code AccountGroupLifecycle} (EDIT_GROUP/DELETE).
 */
public enum AccountGroupContextMenu {

    ADD          ("Add"),
    EDIT_GROUP   ("Edit Group"),
    DELETE       ("Delete"),
    EXPAND_ALL   ("Expand All"),
    COLLAPSE_ALL ("Collapse All");

    private final String label;

    AccountGroupContextMenu(String label) { this.label = label; }

    /**
     * Locator for the context menu item — valid only while the menu is open.
     * Scoped to an ExtJS menu container (x-menu) rather than a bare global text match,
     * since generic labels like "Add" are more likely to collide with unrelated page
     * elements than the more specific labels used by similar menus elsewhere in this app
     * (e.g. Inventory/GroupListPage's "Add New Division"). NOT YET CONFIRMED against the
     * real DOM of this specific context menu — no captured screenshot/HTML has shown it open.
     */
    public By locator() {
        return By.xpath("//div[contains(@class,'x-menu')]//span[text()='" + label + "']");
    }
}
