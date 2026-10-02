package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.exceptions.MandatoryColumnException;
import com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType;
import com.gsl.pages.enums.Finance.Operations.Common.FilterMatchMode;
import com.gsl.pages.enums.Finance.Operations.Common.FilterType;
import com.gsl.pages.enums.Finance.Operations.Common.GridColumnMenuItem;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    /**
     * Retry budget for {@link #openFilterFlyoutAndType} — see its Javadoc for what this guards.
     * Raised from 2 to 4 after a 6th confirmed live recurrence of the same documented hover-flyout
     * auto-collapse race (reports/runs/20260913_141449, FIN-GV-EDIT-DETAIL-02): the retry loop
     * itself already re-opens the flyout from scratch and re-types on every attempt (a real
     * re-click + re-verify each time, not a blind re-poll of the same lost state), but 2 attempts
     * have now been observed to both lose the race back-to-back across several separate runs — see
     * [[feedback_no_threshold_masking]] for why this is a bounded-retry-count increase for an
     * already-documented, physically-real ExtJS timing behavior, not a timeout raised to hide an
     * unexplained symptom.
     */
    private static final int FLYOUT_OPEN_MAX_ATTEMPTS = 4;

    /** One entry in a {@link #sortByPriority(List)} call — priority is the list position. */
    public record SortSpec(IVoucherListColumn column, boolean ascending) {}

    // ------------------------------------------------------------------------------------------
    // Visibility
    // ------------------------------------------------------------------------------------------

    /** Whether {@code column} is currently visible in the live grid (not just declared
     *  non-{@code mandatory()}) — checked via the header's own DOM presence and inline style,
     *  the same {@code display: none} idiom already used throughout this class (see
     *  {@link #visibleHeaderAtPosition}). No JavaScript execution involved;
     *  {@code isElementPresent} polls up to {@code SHORT_TIMEOUT_SECONDS}, which also closes the
     *  race the previous JS-based version had (a one-shot query with no retry). */
    public static boolean isColumnCurrentlyVisible(IVoucherListColumn column) {
        return SeleniumUtils.isElementPresent(visibleHeaderDiv(column), SHORT_TIMEOUT_SECONDS);
    }

    /** {@code column}'s header div, only matching when it is NOT hidden (no
     *  {@code display: none} in its inline style) — see {@link #headerDivXpath}. */
    private static By visibleHeaderDiv(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column) + "[not(contains(@style,'display: none'))]");
    }

    // ------------------------------------------------------------------------------------------
    // Menu lifecycle
    // ------------------------------------------------------------------------------------------

    /** Hovers {@code column}'s header to reveal its dropdown trigger, then clicks it and waits
     *  for the floating {@code x-menu} to appear. {@code column} must currently be visible —
     *  callers needing to reach a hidden column's "Columns ▸" submenu should open the menu on a
     *  currently-visible anchor column instead (see {@link #setColumnVisibility}). Scrolls the
     *  header into view first (native Selenium 4 action, not JS) — GV alone has far more columns
     *  than fit in the viewport at once, so a caller-specified column may be scrolled out of view
     *  in the grid's own horizontal scroll container; a no-op if it's already visible. */
    public static void openColumnMenu(IVoucherListColumn column) {
        closeAnyOpenMenu();
        ActionUtils.scrollToElement(headerDiv(column));
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
        ActionUtils.scrollToElement(visibleHeaderAtPosition(0));
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

    /** Shift+clicks {@code column} directly on its header (not through the menu) to add it to the
     *  grid's multi-sort — preserves any existing sort instead of replacing it (the ExtJS
     *  interaction that builds the {@code (1)}/{@code (2)} badges), but confirmed live
     *  (2026-08-28, two independent runs — one with a retry, one with a single click, identical
     *  result) that the just-clicked column always becomes multi-sort PRIORITY 1, demoting
     *  whatever was already sorted by one position each — not "appended as the next/last
     *  priority" as the name might suggest. See {@link #sortByPriority(List, int)} for how this
     *  affects that method's own click ordering. Returns {@code false} without touching the DOM
     *  if the column isn't currently visible. */
    public static boolean addSortAscending(IVoucherListColumn column) {
        return addSortViaShiftClick(column, true);
    }

    /** Descending counterpart of {@link #addSortAscending}. */
    public static boolean addSortDescending(IVoucherListColumn column) {
        return addSortViaShiftClick(column, false);
    }

    /**
     * Performs exactly ONE Shift+click and never retries it. Confirmed live (2026-08-28, two
     * independent runs): Shift+clicking a column with no existing multi-sort order always
     * promotes it to priority 1, regardless of click count — re-clicking the same column again
     * to try to correct its direction reassigns its order as a side effect instead (this is what
     * previously caused a column to jump to priority 1 when a retry loop existed here). There is
     * no known way to correct direction without re-clicking, and re-clicking corrupts the
     * priority chain, so this method deliberately does not try — it logs a warning if the
     * resulting direction doesn't match what was requested, but still returns {@code true} (the
     * column IS validly part of the multi-sort, just possibly not in the requested direction).
     */
    private static boolean addSortViaShiftClick(IVoucherListColumn column, boolean ascending) {
        if (!isColumnCurrentlyVisible(column)) {
            return false;
        }
        closeAnyOpenMenu();
        // Shift+click bypasses openColumnMenu entirely (it targets the header directly, not the
        // trigger), so it needs its own scroll-into-view — see openColumnMenu's own Javadoc for why.
        ActionUtils.scrollToElement(headerDiv(column));
        ActionUtils.shiftClick(headerDiv(column));
        JavascriptExecutorUtils.waitForExtJS();
        String targetDirection = ascending ? "ascending" : "descending";
        String actualDirection = getSortDirection(column).orElse("none");
        if (!targetDirection.equalsIgnoreCase(actualDirection)) {
            Log.warn("GridColumnMenu.addSort: column [{}] landed as aria-sort='{}' (requested '{}') — "
                    + "not retried, since re-clicking an already-ordered column reassigns its "
                    + "priority as a side effect (confirmed live 2026-08-28).",
                    column.headerText(), actualDirection, targetDirection);
        }
        return true;
    }

    /** Convenience wrapper applying a list of sorts in priority order, capped at the common
     *  {@link com.gsl.config.FrameworkConfig#gridMultiSortMaxColumns()} limit. A module whose
     *  grid supports a different multi-sort limit than the application-wide default should call
     *  {@link #sortByPriority(List, int)} instead, passing its own configured value. */
    public static boolean sortByPriority(List<SortSpec> sortsInPriorityOrder) {
        return sortByPriority(sortsInPriorityOrder, ConfigFactory.getConfig().gridMultiSortMaxColumns());
    }

    /**
     * Same as {@link #sortByPriority(List)}, but with an explicit {@code maxColumns} cap instead
     * of the common default — for a module whose grid supports a different multi-sort limit.
     * Delivers the documented contract (list entry 0 ends up priority 1, entry 1 priority 2, ...)
     * by clicking columns in REVERSE list order internally: confirmed live (2026-08-28) that a
     * Shift+click always promotes the just-clicked column to priority 1, demoting whatever was
     * already sorted — so the intended-LOWEST-priority entry is applied first (via
     * {@link #sortAscending}/{@link #sortDescending}, establishing the sort and replacing any
     * prior one), each higher-priority entry after it (via {@link #addSortAscending}/
     * {@link #addSortDescending}), ending with the intended-priority-1 entry clicked LAST so it
     * lands on top. Returns {@code false} (stopping early) if any entry's column isn't currently
     * visible.
     *
     * @throws IllegalArgumentException if {@code sortsInPriorityOrder} requests more columns than
     *         {@code maxColumns} — confirmed live (2026-08-28) that this app caps multi-sort at a
     *         fixed number of columns, and clicking a new column beyond that cap changes the
     *         existing ordering in a way this class doesn't model; failing fast here is safer
     *         than silently attempting an unsupported N-column sort.
     */
    public static boolean sortByPriority(List<SortSpec> sortsInPriorityOrder, int maxColumns) {
        if (sortsInPriorityOrder == null || sortsInPriorityOrder.isEmpty()) {
            return false;
        }
        if (sortsInPriorityOrder.size() > maxColumns) {
            throw new IllegalArgumentException("GridColumnMenu.sortByPriority: requested "
                    + sortsInPriorityOrder.size() + " sort columns, but at most " + maxColumns
                    + " are supported here.");
        }
        int lastIndex = sortsInPriorityOrder.size() - 1;
        SortSpec lowestPriority = sortsInPriorityOrder.get(lastIndex);
        if (!(lowestPriority.ascending() ? sortAscending(lowestPriority.column()) : sortDescending(lowestPriority.column()))) {
            return false;
        }
        for (int i = lastIndex - 1; i >= 0; i--) {
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

    /** The column's current sort direction ("ascending"/"descending"), if it's part of the
     *  active sort at all. Empty if unsorted, or if the column isn't currently visible. */
    public static Optional<String> getSortDirection(IVoucherListColumn column) {
        if (!isColumnCurrentlyVisible(column)) {
            return Optional.empty();
        }
        String ariaSort = SeleniumUtils.getAttribute(headerDiv(column), "aria-sort",
                column.headerText() + "_ARIA_SORT");
        if (ariaSort == null || "none".equalsIgnoreCase(ariaSort)) {
            return Optional.empty();
        }
        return Optional.of(ariaSort);
    }

    // ------------------------------------------------------------------------------------------
    // Column order — position is mutable independent of mandatory()/hidden state: a mandatory,
    // un-hideable column (Voucher No., Voucher Date) can still be dragged to a different
    // position, and column order differs grid-to-grid (per domain's default layout and whatever
    // a user has already dragged). Never assume a fixed position for any column.
    // ------------------------------------------------------------------------------------------

    /** The 0-based left-to-right position of {@code column} among currently-visible REAL DATA
     *  columns (excluding the row-selection checkbox column and any other structural header with
     *  no sort/filter trigger — see {@link #VISIBLE_DATA_COLUMN_PREDICATE}). Empty if
     *  {@code column} isn't currently visible (position is undefined while hidden). Scans every
     *  currently-visible header's text (DOM order), stripping any multi-sort badge suffix (e.g.
     *  {@code " (1)"}) the same way {@link #sortBadge} reads it — no JavaScript execution involved. */
    public static OptionalInt getColumnPosition(IVoucherListColumn column) {
        if (!isColumnCurrentlyVisible(column)) {
            return OptionalInt.empty();
        }
        List<WebElement> visibleHeaderTexts = DriverManager.getDriver().findElements(VISIBLE_HEADER_TEXT_SPANS);
        for (int i = 0; i < visibleHeaderTexts.size(); i++) {
            String text = visibleHeaderTexts.get(i).getText();
            String stripped = text == null ? "" : text.replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
            if (stripped.startsWith(column.headerText())) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /** Every column in {@code allColumns} that's currently visible, returned in its ACTUAL live
     *  left-to-right order (sorted by {@link #getColumnPosition}) — the real on-screen column
     *  sequence, not the order {@code allColumns} happened to be passed in. Lets a caller assert
     *  or log the whole grid's current column arrangement at once, rather than checking one
     *  column's position at a time. */
    public static List<IVoucherListColumn> getColumnOrder(List<IVoucherListColumn> allColumns) {
        return allColumns.stream()
                .filter(GridColumnMenu::isColumnCurrentlyVisible)
                .sorted(Comparator.comparingInt(c -> getColumnPosition(c).orElse(Integer.MAX_VALUE)))
                .collect(Collectors.toList());
    }

    /** Logs the current sort order, sort direction, and left-to-right position for every column
     *  in {@code allColumns} that is currently visible in the live grid, as ONE readable table
     *  (not a run of flat log lines) — reuses {@link ExtentLogger#infoLabeledTable}, the same
     *  idiom already used elsewhere in this codebase for tabular report data. Columns not
     *  currently visible are skipped entirely (position/sort are undefined while hidden). Rows
     *  are in actual left-to-right order (via {@link #getColumnOrder}), so the table itself
     *  doubles as the column-arrangement record. {@code allColumns} is supplied by the caller's
     *  own domain (e.g. {@code GeneralVoucherMainGrid.allColumns()}) — this method has no
     *  knowledge of any specific domain's column set. */
    public static void logGridState(List<IVoucherListColumn> allColumns) {
        List<IVoucherListColumn> orderedVisible = getColumnOrder(allColumns);

        String[][] table = new String[orderedVisible.size() + 1][4];
        table[0] = new String[]{"Column", "Position", "Sort Order", "Sort Direction"};
        for (int i = 0; i < orderedVisible.size(); i++) {
            IVoucherListColumn column = orderedVisible.get(i);
            OptionalInt position = getColumnPosition(column);
            Optional<Integer> sortOrder = getMultiSortOrder(column);
            Optional<String> sortDirection = getSortDirection(column);
            table[i + 1] = new String[]{
                    column.headerText(),
                    position.isPresent() ? String.valueOf(position.getAsInt() + 1) : "n/a",
                    sortOrder.map(String::valueOf).orElse("none"),
                    sortDirection.orElse("none")
            };
        }
        ExtentLogger.infoLabeledTable("Grid Column State (left-to-right order)", table);
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
        int currentPosition = getColumnPosition(columnToMove).orElseThrow(() -> new IllegalStateException(
                "GridColumnMenu.reorderColumn: column [" + columnToMove.headerText()
                + "] was just confirmed visible but its position could not be read."));
        if (currentPosition == targetPosition) {
            return true;
        }

        closeAnyOpenMenu();
        // Scrolls the SOURCE header into view before dragging — see openColumnMenu's own Javadoc
        // for why. If targetPosition's header is itself off-screen in a different part of the
        // scroll range, that isn't separately guaranteed here — not yet a live-observed problem,
        // since reorderColumn is currently unused in production; flagged, not solved.
        ActionUtils.scrollToElement(headerDiv(columnToMove));
        ActionUtils.dragAndDrop(headerDiv(columnToMove), visibleHeaderAtPosition(targetPosition));
        JavascriptExecutorUtils.waitForExtJS();

        int actualPosition = getColumnPosition(columnToMove).orElseThrow(() -> new IllegalStateException(
                "GridColumnMenu.reorderColumn: column [" + columnToMove.headerText()
                + "] disappeared after the drag — could not read its post-move position."));
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
                + "[@aria-hidden='false'])[last()]"
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

        By input = filterInput(mode);
        openFilterFlyoutAndType(column, mode, input, value);
        // Commits the filter and triggers the store reload — mirrors the existing codebase idiom
        // of sending ENTER to the same locator after typing (see AccountGroupListPage's cell
        // editor). Uses the driver directly (not ActionUtils.sendKeys, which would re-clear the
        // field we just populated).
        //
        // Tolerates ElementNotInteractableException/StaleElementReferenceException here —
        // confirmed live (report 20260831_163724, FIN-GV-EDIT-HEADER-01/02/03): the same
        // documented hover-flyout auto-collapse race (see openFilterFlyoutAndType's Javadoc) can
        // still close the flyout in the narrow window between the type call finishing and this
        // Enter send starting, even with the continued-hover mitigation already in place. The
        // failure screenshot for that exact run showed the grid ALREADY correctly filtered down to
        // the target row at the moment of failure — i.e. ExtJS's own live keyup/change filtering
        // had already committed the value before Enter's own interactability check failed on an
        // element that had already done its job. Swallowing this specific race (rather than
        // propagating) is safe because the caller (selectDocumentIfPresentOnCurrentPage, invoked
        // right after applyFilter returns) independently verifies whether the target row is
        // actually present — that's the real success signal, not whether this keystroke landed.
        //
        // NoSuchElementException joins the same tolerance list: if the flyout has already fully
        // collapsed by the time this findElement runs (rather than merely going stale mid-send),
        // the lookup itself fails to find the input at all — same underlying race, one step
        // earlier, and the caller's independent row-presence check is just as valid a safety net.
        try {
            DriverManager.getDriver().findElement(input).sendKeys(Keys.ENTER);
        } catch (ElementNotInteractableException | StaleElementReferenceException | NoSuchElementException e) {
            Log.warn("[GridColumnMenu] applyFilter: Enter-send on '{}' hit {} (likely the documented "
                    + "hover-flyout auto-collapse race) — the filter value was very likely already "
                    + "committed via ExtJS's own live keyup handling; continuing rather than failing "
                    + "here, since the caller verifies row presence independently.",
                    column.headerText() + "_" + mode + "_FILTER_INPUT", e.getClass().getSimpleName());
            ExtentLogger.warning(String.format(
                    "[Flyout-Race Tolerated] Enter-send on '%s' hit %s — treated as non-fatal, filter "
                    + "value was likely already committed; caller verifies row presence independently.",
                    column.headerText() + "_" + mode + "_FILTER_INPUT", e.getClass().getSimpleName()));
        }
        JavascriptExecutorUtils.waitForExtJS();
        // waitForExtJS() alone only checks the global Ext.Ajax/isMasked component-flag signal —
        // weaker than the real DOM/visual mask check already used for the grid's own refresh
        // button (VoucherListFooterBase -> SeleniumUtils.click -> waitForExtMask). Matching that
        // stronger check here too, since committing this filter triggers the same kind of
        // grid-body reload/loader.
        WaitUtils.waitForExtMask(column.headerText() + "_" + mode + "_FILTER_COMMIT");
        // No closeAnyOpenMenu() here (removed 2026-08-28) — it sent Escape to the still-focused
        // filter input, which reverted the just-committed value (confirmed live, run
        // 20260828_145219: typing succeeded, Enter was sent, yet the row count never moved at
        // all). Unnecessary anyway: openColumnMenu() already calls closeAnyOpenMenu() defensively
        // at the start of every other GridColumnMenu operation, so leftover menu state here gets
        // cleaned up safely by whatever runs next.

        // Debugging aid (2026-08-28, per explicit user request), REPOSITIONED 2026-08-29 — was
        // originally taken immediately after ENTER, before any wait ran, which meant it always
        // showed the pre-update grid regardless of outcome. Moved to here (after the settle wait
        // above) so it actually reflects the committed/settled state instead of a moment before
        // anything visibly changed. Kept (not removed) since CONTAINS mode's live-commit behavior
        // isn't proven yet — still useful evidence if a new bug turns up there.
        ExtentLogger.passWithScreenshot(column.headerText() + "_" + mode + "_after_filter_committed");
        return true;
    }

    /**
     * Opens {@code column}'s "Filters" hover-flyout and types {@code value} into {@code input} —
     * retried up to {@value #FLYOUT_OPEN_MAX_ATTEMPTS} times, RE-OPENING the flyout from scratch
     * on each attempt rather than re-waiting on the same locator. That distinction matters: the
     * failure mode this guards is the flyout auto-collapsing entirely (see the hover-continuation
     * comment below) — once that happens the just-confirmed-visible {@code input} is permanently
     * gone from the DOM, so re-waiting on it can never succeed; only re-running the hover sequence
     * brings a fresh input back.
     *
     * <p>Confirmed live (report 20260901_130916, FIN-GV-EDIT-HEADER-01/03/04 — 3 of 6 failures in
     * that run): the input's visibility was already confirmed with a fast
     * {@link #SHORT_TIMEOUT_SECONDS} wait below, but the previous implementation then typed via
     * {@code SeleniumUtils.sendKeysWithStaleRetry(input, WaitType.VISIBLE, ...)}, whose own
     * internal re-verification of visibility has no timeout override and so silently fell back to
     * the framework's default 30-second wait — burning a full 30s waiting for an input that had
     * already auto-collapsed and would never reappear. Worse, that method's retry only catches
     * {@code StaleElementReferenceException}/{@code ElementNotInteractableException} — not the
     * {@code TimeoutException}/{@code NoSuchElementException} a fully-collapsed flyout actually
     * produces — so its retry never even engaged for this failure mode; the test simply burned
     * 30s and then failed outright. This method fixes both: every wait uses the same short,
     * explicit timeout the initial check already used, and a genuine retry re-opens the flyout
     * instead of re-waiting on a locator that's already permanently gone.
     */
    private static void openFilterFlyoutAndType(IVoucherListColumn column, FilterMatchMode mode, By input, String value) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= FLYOUT_OPEN_MAX_ATTEMPTS; attempt++) {
            try {
                openColumnMenu(column);
                // "Filters" reveals its Contains-anywhere/Exact-match inputs on HOVER, not a
                // click — "Filters" also carries its own enable/disable checkbox (a separate
                // concern from expanding the flyout), so clicking it risks toggling that
                // checkbox instead of opening the flyout. Hover, then wait for the input to
                // actually render before typing into it.
                ActionUtils.moveToElement(GridColumnMenuItem.FILTERS.locator());
                WaitUtils.untilVisible(input, SHORT_TIMEOUT_SECONDS);
                // Continue the hover into the flyout itself before typing — ExtJS auto-collapses
                // a hover-flyout a short time after the mouse stops moving toward it (confirmed
                // live, run 20260828_130058: leaving the cursor parked on "Filters" let the
                // flyout close before typing ran, ~750ms later, producing
                // ElementNotInteractableException every time).
                ActionUtils.moveToElement(input);
                // Fetch + type directly at the SAME short timeout as the visibility check just
                // above — deliberately not SeleniumUtils.sendKeysWithStaleRetry(..., WaitType
                // .VISIBLE, ...), whose default 30s re-verification is exactly the bug this
                // method exists to fix (see class Javadoc).
                WebElement inputEl = WaitUtils.untilVisible(input, SHORT_TIMEOUT_SECONDS);
                inputEl.clear();
                inputEl.sendKeys(value);
                return; // typed successfully
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt < FLYOUT_OPEN_MAX_ATTEMPTS) {
                    Log.warn("[GridColumnMenu] applyFilter: '{}_{}' Filters flyout closed/never "
                            + "opened before typing (attempt {}/{}) — {}: {}. Re-opening and retrying…",
                            column.headerText(), mode, attempt, FLYOUT_OPEN_MAX_ATTEMPTS,
                            e.getClass().getSimpleName(), e.getMessage());
                    ExtentLogger.warning(String.format(
                            "[Filters-Flyout Retry %d/%d] '%s_%s' flyout closed/never opened before "
                            + "typing — re-opening and retrying",
                            attempt, FLYOUT_OPEN_MAX_ATTEMPTS, column.headerText(), mode));
                    closeAnyOpenMenu();
                }
            }
        }
        throw lastFailure;
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
                + "[@aria-hidden='false'])[last()]"
                + "//input[@placeholder='" + mode.placeholderText() + "']");
    }

    // ------------------------------------------------------------------------------------------
    // Column data-cell values (row-level content verification, current page only — see caveat
    // below) — lets a caller check what a filter/sort actually rendered, not just trust the
    // footer's total or a single known-document check.
    // ------------------------------------------------------------------------------------------

    /** The rendered text of every {@code column}'s data cell among rows currently loaded/rendered
     *  on this page — NOT necessarily the grid's full cross-page total (see
     *  {@code VoucherListFooterBase}'s "Displaying X-Y of Z" for that authoritative count; ExtJS
     *  only renders the current page's rows into the DOM). Joins header → cells via
     *  {@code data-columnid}, the same attribute ExtJS stamps on both a column's header div (its
     *  own {@code id}) and every one of its data cells — confirmed live (run 20260828_193340:
     *  Voucher No.'s header {@code id="gridcolumn-1133"} matches its data cells'
     *  {@code data-columnid="gridcolumn-1133"}) — a structural ExtJS rendering convention, not
     *  specific to General Voucher, so this works for any column in any voucher list grid without
     *  per-domain overrides. Reads plain {@code getText()}; a column whose cells render as
     *  something other than plain text (e.g. an icon-only renderer) isn't yet accommodated — none
     *  has been observed to need this, so no speculative extractor override is added ahead of a
     *  real case. */
    public static List<String> getColumnCellValuesOnCurrentPage(IVoucherListColumn column) {
        String headerId = SeleniumUtils.getAttribute(headerDiv(column), "id", column.headerText() + "_HEADER_ID");
        if (headerId == null || headerId.isBlank()) {
            throw new IllegalStateException("GridColumnMenu.getColumnCellValuesOnCurrentPage: column ["
                    + column.headerText() + "] header has no id attribute — cannot join to its data cells.");
        }
        By dataCells = By.xpath("//td[@data-columnid='" + headerId + "']");
        return DriverManager.getDriver().findElements(dataCells).stream()
                .map(WebElement::getText)
                .collect(Collectors.toList());
    }

    /** Counts, among rows currently rendered on this page, how many have {@code column}'s cell
     *  text matching {@code predicate} — e.g. a Voucher No. cell containing a given substring. See
     *  {@link #getColumnCellValuesOnCurrentPage}'s "current page only" caveat. */
    public static long countMatchingRowsOnCurrentPage(IVoucherListColumn column, Predicate<String> predicate) {
        return getColumnCellValuesOnCurrentPage(column).stream().filter(predicate).count();
    }

    /**
     * Multi-column variant — counts, among rows currently rendered on this page, how many satisfy
     * EVERY given column/predicate pair simultaneously (a logical AND across columns), e.g.
     * Voucher No. contains "02182" AND Cash/Bank Name equals "HDFC Bank". Reads each column's
     * cells via {@link #getColumnCellValuesOnCurrentPage} once, then walks rows by INDEX across
     * those lists — safe because {@code findElements} returns matches in document order, and an
     * ExtJS grid row's cells all render together inside one {@code <tr>} before the next row's,
     * so index i in every column's cell list refers to the same physical row.
     *
     * <p>Not yet exercised by any live test (no current scenario needs a simultaneous
     * multi-column filter) — built now as the provision requested ahead of a real multi-filter
     * scenario; the underlying single-column mechanism generalizes directly with no new locator
     * risk.
     *
     * @throws IllegalStateException if two columns' cell-value lists come back different lengths
     *         — would mean the grid changed page/reloaded between reads; call this once per
     *         stable render.
     */
    public static long countMatchingRowsOnCurrentPage(Map<IVoucherListColumn, Predicate<String>> columnPredicates) {
        if (columnPredicates.isEmpty()) {
            return 0;
        }
        Map<IVoucherListColumn, List<String>> valuesByColumn = new LinkedHashMap<>();
        int rowCount = -1;
        for (IVoucherListColumn column : columnPredicates.keySet()) {
            List<String> values = getColumnCellValuesOnCurrentPage(column);
            if (rowCount == -1) {
                rowCount = values.size();
            } else if (values.size() != rowCount) {
                throw new IllegalStateException("GridColumnMenu.countMatchingRowsOnCurrentPage: column ["
                        + column.headerText() + "] returned " + values.size() + " cell values, but a "
                        + "previous column in this same call returned " + rowCount + " — the grid may "
                        + "have changed page/reloaded between reads; call this once per stable render.");
            }
            valuesByColumn.put(column, values);
        }
        long matches = 0;
        for (int row = 0; row < rowCount; row++) {
            int r = row;
            boolean allMatch = columnPredicates.entrySet().stream()
                    .allMatch(e -> e.getValue().test(valuesByColumn.get(e.getKey()).get(r)));
            if (allMatch) {
                matches++;
            }
        }
        return matches;
    }

    // ------------------------------------------------------------------------------------------
    // Locators — confirmed via live DevTools capture (see plan Step 0)
    // ------------------------------------------------------------------------------------------

    // aria-hidden='false' (not the display:none-based hidden-element exclusion used for column
    // headers below) — confirmed live, run 20260828_160502: ExtJS keeps a floating submenu
    // instance around hidden-via-visibility:hidden (not display:none) for reuse after it's been
    // shown once, and that stale instance can sit LATER in DOM order than a freshly-opened real
    // menu, making a plain "last non-display:none x-menu" pick the wrong (stale) one.
    // aria-hidden/aria-expanded are ExtJS's own semantic signal for "is this actually shown" and
    // aren't tied to any one particular CSS hiding mechanism.
    private static final By OPEN_MENU = By.xpath(
            "(//div[contains(concat(' ', normalize-space(@class), ' '), ' x-menu ')]"
            + "[@aria-hidden='false'])[last()]");

    /** A "real" data column header — visible AND carrying a {@code triggerEl} (sort/filter/columns
     *  dropdown). Confirmed live (run 20260828_193340): this grid's header row also includes a
     *  tree/expander column ({@code treecolumn-*}) and the row-selection checkbox column
     *  ({@code checkcolumn-*}), BOTH before any real data column in DOM order — both pass a plain
     *  "x-column-header, not hidden" check but carry no {@code triggerEl} (verified across every
     *  header div in that capture: every real data column has one, only these 2 structural
     *  columns don't). Excluding them keeps "position 0" meaning the same thing everywhere this
     *  predicate is used — the anchor {@link #openColumnMenuOnFirstVisibleColumn} hovers, AND the
     *  position {@link #getColumnPosition}/{@link #logGridState} report — rather than crashing on
     *  (or silently miscounting from) a non-interactive structural header. */
    private static final String VISIBLE_DATA_COLUMN_PREDICATE =
            "[contains(concat(' ', normalize-space(@class), ' '), ' x-column-header ')]"
            + "[not(contains(@style,'display: none'))]"
            + "[.//div[@data-ref='triggerEl']]";

    /** The real-data-column header (DOM order, among visible+triggerable ones only) at 0-based
     *  {@code position} — used both for {@link #openColumnMenuOnFirstVisibleColumn} (position 0)
     *  and as the drop target in {@link #reorderColumn}. See {@link #VISIBLE_DATA_COLUMN_PREDICATE}. */
    private static By visibleHeaderAtPosition(int position) {
        return By.xpath("(//div" + VISIBLE_DATA_COLUMN_PREDICATE + ")[" + (position + 1) + "]");
    }

    private static By visibleHeaderTriggerAtPosition(int position) {
        return By.xpath("(//div" + VISIBLE_DATA_COLUMN_PREDICATE + ")[" + (position + 1)
                + "]//div[@data-ref='triggerEl']");
    }

    /** Every currently-visible real-data-column header's text-inner span, in DOM (left-to-right)
     *  order — used by {@link #getColumnPosition} to compute a column's 0-based visible position
     *  without any JavaScript execution. See {@link #VISIBLE_DATA_COLUMN_PREDICATE}. */
    private static final By VISIBLE_HEADER_TEXT_SPANS = By.xpath(
            "//div" + VISIBLE_DATA_COLUMN_PREDICATE + "//span[@data-ref='textInnerEl']");

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

    /** The hover-reveal dropdown-arrow trigger. Confirmed live (run 20260828_123857's captured
     *  HTML) to be nested one level deeper than originally assumed — a child of {@code titleEl},
     *  sibling of {@code textContainerEl} (not a direct child of {@code headerDiv} itself):
     *  {@code headerDiv > titleEl > (textContainerEl, triggerEl)}. Uses the descendant axis
     *  ({@code //}) rather than hardcoding the {@code titleEl} hop, matching {@link #sortBadge}'s
     *  existing idiom for reaching into a header's internals. */
    private static By triggerEl(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column) + "//div[@data-ref='triggerEl']");
    }

    /** The multi-sort priority badge, e.g. {@code <small><span>(1)</span></small>} inside the
     *  header's {@code data-ref="textInnerEl"} span (confirmed live). */
    private static By sortBadge(IVoucherListColumn column) {
        return By.xpath(headerDivXpath(column) + "//span[@data-ref='textInnerEl']/small/span");
    }
}
