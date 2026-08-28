package com.gsl.pages.enums.Finance.Operations.Common;

import org.openqa.selenium.By;

/**
 * Items in the floating ExtJS menu opened by clicking a grid column header's dropdown trigger
 * ({@code x-column-header-trigger}) — Sort Ascending / Sort Descending / a "Columns ▸" submenu
 * (show/hide checkboxes) / a "Filters" checkbox-expandable section. Follows the same
 * label+locator idiom as {@link com.gsl.pages.enums.Finance.Setup.AccountGroupContextMenu} for
 * ExtJS {@code x-menu} items.
 *
 * <p>Scoped to the most recently opened {@code x-menu} (i.e. {@code [last()]}), not just any
 * {@code x-menu} on the page — the "Columns ▸"/"Filters" flyouts render as separate sibling
 * {@code x-menu} nodes once expanded, so more than one can be present in the DOM at once (see
 * live evidence captured for the TDS Rate column's Filters flyout, GridColumnMenu plan Step 0).
 */
public enum GridColumnMenuItem {

    SORT_ASCENDING("Sort Ascending"),
    SORT_DESCENDING("Sort Descending"),
    COLUMNS("Columns"),
    FILTERS("Filters");

    private final String label;

    GridColumnMenuItem(String label) { this.label = label; }

    public String label() { return label; }

    /** Locator for this menu item — valid only while the top-level column-header menu is open. */
    public By locator() {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
                + "[not(contains(@style,'display: none'))])[last()]"
                + "//span[normalize-space()='" + label + "']");
    }
}
