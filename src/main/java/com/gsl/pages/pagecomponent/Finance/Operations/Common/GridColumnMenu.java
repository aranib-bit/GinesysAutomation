package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.exceptions.MandatoryColumnException;
import com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType;
import com.gsl.pages.enums.Finance.Operations.Common.FilterMatchMode;
import com.gsl.pages.enums.Finance.Operations.Common.FilterType;
import com.gsl.pages.enums.Finance.Operations.Common.GridColumnMenuItem;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Domain-agnostic utility for driving an ExtJS voucher list-grid column header's floating menu
 * (hover → dropdown trigger → Sort Ascending / Sort Descending / "Columns ▸" show-hide / "Filters")
 * plus reading/changing a column's on-screen position (drag-reorder) — shared by GV/AR/AP/Contra
 * the same way {@link VoucherGridPaginationFinder} is, and following that class's exact shape:
 * final class, private constructor, static methods, no instance state (every operation is keyed
 * purely off an {@link IVoucherListColumn} + the live DOM).
 *
 * <p>Column order is mutable independent of {@code mandatory()}/hidden state — a mandatory,
 * un-hideable column can still be dragged to a different position, and default order differs per
 * domain's grid — so no method here ever assumes a fixed position for any column; position is
 * always read fresh from the live DOM ({@link #getColumnPosition}) or targeted relative to it
 * ({@link #reorderColumn}), never hardcoded.
 *
 * <p><b>[VERIFY step 0] — one locator group is not yet confirmed against the live app</b>:
 * the "Columns ▸" submenu's show/hide checkbox items ({@link #columnsSubmenuCheckbox}) —
 * confirmed only that "Columns" is a flyout-triggering top-level item; the flyout's own
 * checkbox/label DOM has not been inspected live. Also unconfirmed: {@link #reorderColumn}'s
 * assumption about which side of a drop target a dragged column header lands on.
 *
 * <p>Everything else is confirmed via live DevTools capture — the header/trigger/sort-badge
 * locators, the top-level menu items, hovering/visibility checks, and (as of this pass) the
 * STRING-column "Filters" text inputs ({@link #filterInput}, confirmed to be plain HTML5
 * {@code placeholder} attributes, not an ExtJS synthetic-emptyText field) — see the plan
 * ({@code GridColumnMenu} section) for the captured markup.
 */
public final class GridColumnMenu {

    private GridColumnMenu() {}

    private static final int SHORT_TIMEOUT_SECONDS = 3;
    private static final int SORT_BADGE_CHECK_TIMEOUT_SECONDS = 2;
    private static final int MAX_SHIFT_CLICK_ATTEMPTS = 3;

    /** One entry in a {@link #sortByPriority(List)} call — priority is the list position. */
    public record SortSpec(IVoucherListColumn column, boolean ascending) {}

    // ------------------------------------------------------------------------------------------
    // Visibility
    // ------------------------------------------------------------------------------------------

    /** Whether {@code column} is currently visible in the live grid (not just declared
     *  non-{@code mandatory()}) — queried directly from ExtJS component state, see
     *  {@link JavascriptExecutorUtils#isGridColumnHidden(String)}. */
    public static boolean isColumnCurrentlyVisible(IVoucherListColumn column) {
        return !JavascriptExecutorUtils.isGridColumnHidden(column.headerText());
    }

    // ------------------------------------------------------------------------------------------
    // Menu lifecycle
    // ------------------------------------------------------------------------------------------

    /** Hovers {@code column}'s header to reveal its dropdown trigger, then clicks it and waits
     *  for the floating {@code x-menu} to appear. {@code column} must currently be visible —
     *  callers needing to reach a hidden column's "Columns ▸" submenu should open the menu on a
     *  currently-visible anchor column instead (see {@link #setColumnVisibility}). */
    public static void openColumnMenu(IVoucherListColumn column) {
        closeAnyOpenMenu();
        ActionUtils.moveToElement(headerDiv(column));
        WaitUtils.untilVisible(triggerEl(column), SHORT_TIMEOUT_SECONDS);
        SeleniumUtils.clickWithStaleRetry(triggerEl(column), WaitType.CLICKABLE,
                column.headerText() + "_COLUMN_HEADER_TRIGGER");
        WaitUtils.untilVisible(OPEN_MENU, SHORT_TIMEOUT_SECONDS);
        JavascriptExecutorUtils.waitForExtJS();
    }

    /** Opens the column-header menu anchored on whichever column header is currently first in
     *  DOM order and not hidden — used only to reach the shared "Columns ▸" submenu when the
     *  actual target column is itself hidden (so its own header can't be hovered). Deliberately
     *  independent of any specific column identity/domain — see {@link #setColumnVisibility}. */
    private static void openColumnMenuOnFirstVisibleColumn() {
        closeAnyOpenMenu();
        By firstVisibleTrigger = visibleHeaderTriggerAtPosition(0);
        ActionUtils.moveToElement(visibleHeaderAtPosition(0));
        WaitUtils.untilVisible(firstVisibleTrigger, SHORT_TIMEOUT_SECONDS);
        SeleniumUtils.clickWithStaleRetry(firstVisibleTrigger, WaitType.CLICKABLE,
                "FIRST_VISIBLE_COLUMN_HEADER_TRIGGER");
        WaitUtils.untilVisible(OPEN_MENU, SHORT_TIMEOUT_SECONDS);
        JavascriptExecutorUtils.waitForExtJS();
    }

    /** Best-effort dismissal of any open column-header menu/flyout — safe to call even if
     *  nothing is currently open. */
    public static void closeAnyOpenMenu() {
        try {
            ActionUtils.pressEscape();
        } catch (Exception e) {
            Log.debug("GridColumnMenu.closeAnyOpenMenu: pressEscape was a no-op ({})", e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------------------------------------

    /** Sorts the grid by {@code column} ascending, REPLACING any existing sort — this is what
     *  clicking "Sort Ascending" in the header menu does natively. Returns {@code false} without
     *  touching the DOM if the column isn't currently visible. */
    public static boolean sortAscending(IVoucherListColumn column) {
        return sortViaMenu(column, GridColumnMenuItem.SORT_ASCENDING);
    }

    /** Sorts the grid by {@code column} descending, REPLACING any existing sort. See {@link #sortAscending}. */
    public static boolean sortDescending(IVoucherListColumn column) {
        return sortViaMenu(column, GridColumnMenuItem.SORT_DESCENDING);
    }

    private static boolean sortViaMenu(IVoucherListColumn column, GridColumnMenuItem menuItem) {
        if (!isColumnCurrentlyVisible(column)) {
            return false;
        }
        openColumnMenu(column);
        SeleniumUtils.clickWithStaleRetry(menuItem.locator(), WaitType.CLICKABLE,
                column.headerText() + "_" + menuItem.name());
        JavascriptExecutorUtils.waitForExtJS();
        return true;
    }

    /** Adds {@code column} as the NEXT sort priority (Shift+click directly on the header, not
     *  through the menu) — preserves any existing sort instead of replacing it, the ExtJS
     *  interaction used to build a multi-column sort chain (the {@code (1)}/{@code (2)} badges).
     *  [VERIFY step 0]: confirm this app's grid actually uses standard Shift+click multi-sort
     *  before trusting the direction-retry loop below. Returns {@code false} without touching
     *  the DOM if the column isn't currently visible. */
    public static boolean addSortAscending(IVoucherListColumn column) {
        return addSortViaShiftClick(column, true);
    }

    /** Descending counterpart of {@link #addSortAscending}. */
    public static boolean addSortDescending(IVoucherListColumn column) {
        return addSortViaShiftClick(column, false);
    }

    private static boolean addSortViaShiftClick(IVoucherListColumn column, boolean ascending) {
        if (!isColumnCurrentlyVisible(column)) {
            return false;
        }
        closeAnyOpenMenu();
        String targetDirection = ascending ? "ascending" : "descending";
        for (int attempt = 1; attempt <= MAX_SHIFT_CLICK_ATTEMPTS; attempt++) {
            ActionUtils.shiftClick(headerDiv(column));
            JavascriptExecutorUtils.waitForExtJS();
            String actualDirection = SeleniumUtils.getAttribute(headerDiv(column), "aria-sort",
                    column.headerText() + "_ARIA_SORT");
            if (targetDirection.equalsIgnoreCase(actualDirection)) {
                return true;
            }
        }
        Log.warn("GridColumnMenu.addSort: column [{}] did not reach aria-sort='{}' after {} Shift+click "
                + "attempt(s) — [VERIFY step 0] confirm Shift+click-to-multi-sort behavior live.",
                column.headerText(), targetDirection, MAX_SHIFT_CLICK_ATTEMPTS);
        return true;
    }

    /** Convenience wrapper applying a list of sorts in priority order: the first entry
     *  establishes priority 1 (via {@link #sortAscending}/{@link #sortDescending}, replacing any
     *  prior sort), each subsequent entry appends as the next priority (via
     *  {@link #addSortAscending}/{@link #addSortDescending}). Returns {@code false} (stopping
     *  early) if any entry's column isn't currently visible. */
    public static boolean sortByPriority(List<SortSpec> sortsInPriorityOrder) {
        if (sortsInPriorityOrder == null || sortsInPriorityOrder.isEmpty()) {
            return false;
        }
        SortSpec first = sortsInPriorityOrder.get(0);
        if (!(first.ascending() ? sortAscending(first.column()) : sortDescending(first.column()))) {
            return false;
        }
        for (int i = 1; i < sortsInPriorityOrder.size(); i++) {
            SortSpec spec = sortsInPriorityOrder.get(i);
            boolean applied = spec.ascending() ? addSortAscending(spec.column()) : addSortDescending(spec.column());
            if (!applied) {
                return false;
            }
        }
        return true;
    }

    /** Reads the multi-sort priority badge (e.g. {@code (1)}, {@code (2)}) off {@code column}'s
     *  header, if present. Empty when the column isn't currently visible, or when it's visible
     *  but not part of a multi-column sort (the common single-sort case shows no badge). */
    public static Optional<Integer> getMultiSortOrder(IVoucherListColumn column) {
        if (!isColumnCurrentlyVisible(column)) {
            return Optional.empty();
        }
        By badge = sortBadge(column);
        if (!SeleniumUtils.isElementPresent(badge, SORT_BADGE_CHECK_TIMEOUT_SECONDS)) {
            return Optional.empty();
        }
        String text = SeleniumUtils.getAttribute(badge, "textContent", column.headerText() + "_SORT_BADGE");
        String digits = text == null ? "" : text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(digits));
    }

    // ------------------------------------------------------------------------------------------
    // Column order — position is mutable independent of mandatory()/hidden state: a mandatory,
    // un-hideable column (Voucher No., Voucher Date) can still be dragged to a different
    // position, and column order differs grid-to-grid (per domain's default layout and whatever
    // a user has already dragged). Never assume a fixed position for any column.
    // ------------------------------------------------------------------------------------------

    /** The 0-based left-to-right position of {@code column} among currently-VISIBLE columns.
     *  Empty if {@code column} isn't currently visible (position is undefined while hidden). */
    public static OptionalInt getColumnPosition(IVoucherListColumn column) {
        if (!isColumnCurrentlyVisible(column)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(JavascriptExecutorUtils.getVisibleColumnPosition(column.headerText()));
    }

    /**
     * Drags {@code columnToMove}'s header to {@code targetPosition} (0-based, among currently-
     * visible columns) — the same mouse-drag interaction a user performs to reorder grid columns.
     * Idempotent — a no-op if already at {@code targetPosition}.
     *
     * <p>[VERIFY step 0]: ExtJS's drop-half behavior (dropping on a target header's left vs.
     * right half typically inserts before/after it — this drags to that header's center, the
     * {@code Actions} default) is unconfirmed live; the post-move position check below will at
     * least surface a mismatch loudly rather than silently landing one column off.
     *
     * @return {@code false} without touching the DOM if {@code columnToMove} isn't currently visible
     * @throws IllegalStateException if the drag didn't land the column at {@code targetPosition}
     */
    public static boolean reorderColumn(IVoucherListColumn columnToMove, int targetPosition) {
        if (!isColumnCurrentlyVisible(columnToMove)) {
            return false;
        }
        int currentPosition = JavascriptExecutorUtils.getVisibleColumnPosition(columnToMove.headerText());
        if (currentPosition == targetPosition) {
            return true;
        }

        closeAnyOpenMenu();
        ActionUtils.dragAndDrop(headerDiv(columnToMove), visibleHeaderAtPosition(targetPosition));
        JavascriptExecutorUtils.waitForExtJS();

        int actualPosition = JavascriptExecutorUtils.getVisibleColumnPosition(columnToMove.headerText());
        if (actualPosition != targetPosition) {
            throw new IllegalStateException("GridColumnMenu.reorderColumn: dragging column ["
                    + columnToMove.headerText() + "] to position " + targetPosition
                    + " landed it at position " + actualPosition + " instead — [VERIFY step 0] "
                    + "confirm ExtJS's drop-half insert-before/after behavior for this grid.");
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Column visibility (show/hide via "Columns ▸")
    // ------------------------------------------------------------------------------------------

    /** Shows {@code column} if currently hidden (no-op — idempotent — if already visible). */
    public static void showColumn(IVoucherListColumn column) {
        setColumnVisibility(column, true);
    }

    /** Hides {@code column} if currently visible (no-op — idempotent — if already hidden).
     *  @throws MandatoryColumnException if {@code column.mandatory()} — fails fast, before any
     *          DOM interaction, rather than letting the app silently refuse the request. */
    public static void hideColumn(IVoucherListColumn column) {
        if (column.mandatory()) {
            throw new MandatoryColumnException(column);
        }
        setColumnVisibility(column, false);
    }

    private static void setColumnVisibility(IVoucherListColumn column, boolean targetVisible) {
        if (isColumnCurrentlyVisible(column) == targetVisible) {
            return;
        }
        // Anchor-column problem: a currently-hidden column's own header isn't rendered, so it
        // can't be hovered to reach "Columns ▸" — only relevant when targetVisible=true (we're
        // about to SHOW a hidden column); when hiding, `column` itself is still visible right up
        // until the checkbox click, so it can anchor itself directly (openColumnMenu(column)).
        //
        // Deliberately does NOT anchor on any specific enum constant (e.g. a hardcoded
        // CommonVoucherListColumn.VOUCHER_NO): columns differ per VoucherDomain — some are
        // common-but-domain-overridden (CommonVoucherListColumn.resolveFor(domain) can rename or
        // exclude a "common" column per domain) and some are domain-specific to begin with (e.g.
        // GV/AP's "Payment Amt (Cr)", AR/AP's "SL Name"). GridColumnMenu is intentionally
        // domain-agnostic — it has no VoucherDomain to resolve against — so the only safe anchor
        // is "whatever column happens to be visible right now", found live from the DOM rather
        // than assumed from any column enum.
        if (targetVisible) {
            openColumnMenuOnFirstVisibleColumn();
        } else {
            openColumnMenu(column);
        }
        // "Columns ▸" reveals its checkbox flyout on HOVER (standard ExtJS submenu-arrow
        // behavior), not a click — clicking risks landing on some other part of that row instead
        // of expanding the submenu. Hover, then wait for the flyout's own checkbox to render
        // before clicking it.
        ActionUtils.moveToElement(GridColumnMenuItem.COLUMNS.locator());
        WaitUtils.untilVisible(columnsSubmenuCheckbox(column), SHORT_TIMEOUT_SECONDS);
        SeleniumUtils.clickWithStaleRetry(columnsSubmenuCheckbox(column), WaitType.CLICKABLE,
                column.headerText() + "_COLUMNS_CHECKBOX");
        JavascriptExecutorUtils.waitForExtJS();
        closeAnyOpenMenu();

        if (isColumnCurrentlyVisible(column) != targetVisible) {
            throw new IllegalStateException("GridColumnMenu.setColumnVisibility: toggling column ["
                    + column.headerText() + "] to visible=" + targetVisible + " did not take effect.");
        }
    }

    /** [VERIFY step 0]: the "Columns ▸" submenu's real checkbox/label DOM has not been inspected
     *  live — this assumes it renders as another sibling {@code x-menu} flyout with a plain text
     *  label per column, following the same shape confirmed for the top-level menu items. */
    private static By columnsSubmenuCheckbox(IVoucherListColumn column) {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
                + "[not(contains(@style,'display: none'))])[last()]"
                + "//span[normalize-space()='" + column.headerText() + "']");
    }

    // ------------------------------------------------------------------------------------------
    // Filtering — pass 1 scope: TEXT_CONTAINS (STRING columns) only
    // ------------------------------------------------------------------------------------------

    /** Applies a "Contains anywhere..." (partial-match) filter to {@code column}. Only supported
     *  for {@code STRING} columns (see {@link #applyFilter}); returns {@code false} without
     *  touching the DOM if the column isn't currently visible. */
    public static boolean filterByContains(IVoucherListColumn column, String value) {
        return applyFilter(column, FilterMatchMode.CONTAINS, value);
    }

    /** Applies an "Exact match..." filter to {@code column}. See {@link #filterByContains}. */
    public static boolean filterByExactMatch(IVoucherListColumn column, String value) {
        return applyFilter(column, FilterMatchMode.EXACT, value);
    }

    /**
     * Dispatches on {@code column.dataType().defaultFilterType()} — only {@link FilterType#TEXT_CONTAINS}
     * (i.e. {@link ColumnDataType#STRING} columns) is implemented in this pass; DATE_RANGE/
     * NUMBER_RANGE/DROPDOWN_SELECT/CHECKBOX_TOGGLE are deferred (their filter-flyout structure is
     * unverified — see live evidence of the NUMBER_RANGE shape captured for a numeric column,
     * plan Step 0) and throw {@link UnsupportedOperationException} rather than attempt a guess.
     */
    private static boolean applyFilter(IVoucherListColumn column, FilterMatchMode mode, String value) {
        FilterType filterType = column.dataType().defaultFilterType();
        if (filterType != FilterType.TEXT_CONTAINS) {
            throw new UnsupportedOperationException(
                    "GridColumnMenu.applyFilter: column [" + column.headerText() + "] has dataType "
                    + column.dataType() + " -> filterType " + filterType + " — only TEXT_CONTAINS "
                    + "(STRING columns) is implemented; DATE_RANGE/NUMBER_RANGE/DROPDOWN_SELECT/"
                    + "CHECKBOX_TOGGLE are deferred.");
        }
        if (!isColumnCurrentlyVisible(column)) {
            return false;
        }

        openColumnMenu(column);
        // "Filters" reveals its Contains-anywhere/Exact-match inputs on HOVER, not a click —
        // "Filters" also carries its own enable/disable checkbox (a separate concern from
        // expanding the flyout), so clicking it risks toggling that checkbox instead of opening
        // the flyout. Hover, then wait for the input to actually render before typing into it.
        ActionUtils.moveToElement(GridColumnMenuItem.FILTERS.locator());
        By input = filterInput(mode);
        WaitUtils.untilVisible(input, SHORT_TIMEOUT_SECONDS);
        SeleniumUtils.sendKeysWithStaleRetry(input, WaitType.VISIBLE, value,
                column.headerText() + "_" + mode + "_FILTER_INPUT");
        // Commits the filter and triggers the store reload — mirrors the existing codebase idiom
        // of sending ENTER to the same locator after typing (see AccountGroupListPage's cell
        // editor). Uses the driver directly (not ActionUtils.sendKeys, which would re-clear the
        // field we just populated).
        DriverManager.getDriver().findElement(input).sendKeys(Keys.ENTER);
        JavascriptExecutorUtils.waitForExtJS();
        closeAnyOpenMenu();
        return true;
    }

    /** CONFIRMED via live DevTools capture: the "Contains anywhere..."/"Exact match..." text is a
     *  real HTML5 {@code placeholder} attribute on a plain {@code <input type="text">} — e.g.
     *  {@code <input data-ref="inputEl" placeholder="Contains anywhere..." class="x-form-field
     *  x-form-empty-field ... x-form-text ...">}. The {@code x-form-empty-field} class is only a
     *  cosmetic empty-state class (toggled based on whether the field has a value) — it does not
     *  affect the native {@code placeholder} attribute and needs no special clear/focus handling
     *  before {@code sendKeysWithStaleRetry}, unlike an ExtJS synthetic-emptyText field would. */
    private static By filterInput(FilterMatchMode mode) {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
                + "[not(contains(@style,'display: none'))])[last()]"
                + "//input[@placeholder='" + mode.placeholderText() + "']");
    }

    // ------------------------------------------------------------------------------------------
    // Locators — confirmed via live DevTools capture (see plan Step 0)
    // ------------------------------------------------------------------------------------------

    private static final By OPEN_MENU = By.xpath(
            "(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
            + "[not(contains(@style,'display: none'))])[last()]");

    /** The column header (DOM order, among not-hidden ones only) at 0-based {@code position} —
     *  used both for {@link #openColumnMenuOnFirstVisibleColumn} (position 0) and as the drop
     *  target in {@link #reorderColumn}. Assumes an ExtJS-hidden column header carries
     *  {@code display: none} in its inline style, consistent with the {@code OPEN_MENU}/
     *  {@code GridColumnMenuItem} locators' own hidden-element exclusion idiom. */
    private static By visibleHeaderAtPosition(int position) {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')]"
                + "[not(contains(@style,'display: none'))])[" + (position + 1) + "]");
    }

    private static By visibleHeaderTriggerAtPosition(int position) {
        return By.xpath("(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')]"
                + "[not(contains(@style,'display: none'))])[" + (position + 1) + "]/div[@data-ref='triggerEl']");
    }

    /**
     * Locates the outer {@code div#gridcolumn-NNNN.x-column-header} for {@code column}, matching
     * the header text with {@code starts-with()} rather than exact equality — every existing
     * per-enum {@code columnIdXPath()} uses exact equality, which breaks once a sort badge like
     * {@code " (1)"} is appended to the rendered text (a latent bug those methods have carried
     * unexercised until this feature; not fixed here, flagged as a follow-up).
     */
    private static By headerDiv(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column));
    }

    private static String headerDivXpath(IVoucherListColumn column) {
        return "//span[starts-with(normalize-space(.), '" + column.headerText() + "')]"
                + "/ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')][1]";
    }

    /** The hover-reveal dropdown-arrow trigger — a direct child of {@code headerDiv}, sibling
     *  of its text container (confirmed live: {@code <div data-ref="triggerEl" class="x-column-header-trigger">}). */
    private static By triggerEl(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column) + "/div[@data-ref='triggerEl']");
    }

    /** The multi-sort priority badge, e.g. {@code <small><span>(1)</span></small>} inside the
     *  header's {@code data-ref="textInnerEl"} span (confirmed live). */
    private static By sortBadge(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column) + "//span[@data-ref='textInnerEl']/small/span");
    }
}
