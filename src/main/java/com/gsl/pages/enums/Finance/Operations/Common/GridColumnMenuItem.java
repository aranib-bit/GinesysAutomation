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

    /** Locator for this menu item — valid only while the top-level column-header menu is open.
     *  Uses {@code @aria-hidden='false'} (not a {@code display: none} style check) to pick the
     *  one truly-open {@code x-menu} — ExtJS keeps a previously-shown floating submenu instance
     *  around hidden via {@code visibility: hidden} for reuse, and that stale instance can sit
     *  later in DOM order than a freshly-opened real menu, so a plain "last non-hidden x-menu"
     *  can pick the wrong one (confirmed live, run 20260828_162852). */
    public By locator() {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
                + "[@aria-hidden='false'])[last()]"
                + "//span[normalize-space()='" + label + "']");
    }
}
