package com.gsl.pages.enums.Finance.Setup;

import org.openqa.selenium.By;

/**
 * Items under the Account Class list page's "Actions" dropdown button.
 *
 * ⚠️ UNVERIFIED — no "Actions" dropdown button precedent exists anywhere else in this codebase
 * (confirmed by repo-wide search); this locator shape is a best guess modelled on
 * {@code AccountGroupContextMenu}'s right-click menu convention (an ExtJS {@code x-menu} opened
 * by a trigger, with items matched by visible span text) and must be confirmed against the real
 * DOM on the first live run.
 */
public enum AccountClassActionsMenu {

    EDIT("Edit"),
    EXPORT_TO_EXCEL("Export to Excel");

    private final String label;

    AccountClassActionsMenu(String label) {
        this.label = label;
    }

    /** This item's visible dropdown text, e.g. {@code "Edit"}. */
    public String label() {
        return label;
    }

    /** Locator for this item — valid only while the "Actions" dropdown is open. */
    public By locator() {
        return By.xpath("//div[contains(@class,'x-menu')]//span[text()='" + label + "']");
    }
}
