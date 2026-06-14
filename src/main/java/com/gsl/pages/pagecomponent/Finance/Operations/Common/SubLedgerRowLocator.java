package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import org.openqa.selenium.By;

/**
 * Builds row-anchor XPaths for the AR/AP Voucher sub-ledger grid.
 *
 * <p>Provides two variants for each locator:
 * <ul>
 *   <li>SL name only — confirmed working in live DevTools</li>
 *   <li>SL name + SL ID — more precise; preferred when slId is available</li>
 * </ul>
 *
 * Column IDs for amount cells must be resolved from the domain-aware enum
 * ({@code CommonVoucherGridColumnHeader}) and passed as {@code columnId} strings.
 */
public final class SubLedgerRowLocator {

    private static final String DOTS_BY_NAME =
            "//tr[td/div[contains(@class,'x-grid-cell-inner') and text()='%s']]" +
            "//i[contains(@class,'mdi-dots-horizontal')]";

    private static final String DOTS_BY_NAME_AND_ID =
            "//tr[td/div[contains(@class,'x-grid-cell-inner') and text()='%s']" +
            " and td/div[contains(@class,'x-grid-cell-inner') and normalize-space()='%s']]" +
            "//i[contains(@class,'mdi-dots-horizontal')]";

    private static final String AMOUNT_CELL_BY_NAME =
            "//tr[td/div[contains(@class,'x-grid-cell-inner') and text()='%s']]" +
            "//td[@data-columnid='%s']";

    private static final String AMOUNT_CELL_BY_NAME_AND_ID =
            "//tr[td/div[contains(@class,'x-grid-cell-inner') and text()='%s']" +
            " and td/div[contains(@class,'x-grid-cell-inner') and normalize-space()='%s']]" +
            "//td[@data-columnid='%s']";

    private SubLedgerRowLocator() {}

    /** By for the three-dots hover target — SL name only. */
    public static By dotsButton(String slName) {
        return By.xpath(String.format(DOTS_BY_NAME, slName));
    }

    /** By for the three-dots hover target — SL name + SL ID (preferred). Falls back to name-only if slId is blank. */
    public static By dotsButton(String slName, String slId) {
        if (slId == null || slId.isBlank()) return dotsButton(slName);
        return By.xpath(String.format(DOTS_BY_NAME_AND_ID, slName, slId));
    }

    /**
     * By for an amount cell, anchored by SL name only.
     *
     * @param slName     sub-ledger display name
     * @param columnId   resolved column ID string from the domain enum (e.g. {@code "gridcolumn-1234"})
     */
    public static By amountCell(String slName, String columnId) {
        return By.xpath(String.format(AMOUNT_CELL_BY_NAME, slName, columnId));
    }

    /**
     * By for an amount cell, anchored by SL name + SL ID (preferred).
     * Falls back to name-only when slId is blank.
     *
     * @param slName     sub-ledger display name
     * @param slId       sub-ledger code / ID shown in the SL ID column
     * @param columnId   resolved column ID string from the domain enum
     */
    public static By amountCell(String slName, String slId, String columnId) {
        if (slId == null || slId.isBlank()) return amountCell(slName, columnId);
        return By.xpath(String.format(AMOUNT_CELL_BY_NAME_AND_ID, slName, slId, columnId));
    }
}
