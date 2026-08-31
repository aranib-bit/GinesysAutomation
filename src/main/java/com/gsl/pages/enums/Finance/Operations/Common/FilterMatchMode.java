package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * The two text-match modes offered in an ExtJS grid column's "Filters" flyout for a
 * {@code STRING}-typed column ({@link ColumnDataType#STRING}, {@link FilterType#TEXT_CONTAINS}):
 * a partial/"contains anywhere" match and an exact match. Carries the flyout's placeholder text
 * for each input, used to build that input's locator.
 *
 * <p>Placeholder text confirmed via live DevTools capture for both modes — a real HTML5
 * {@code placeholder} attribute, not an ExtJS synthetic {@code emptyText} value:
 * {@code textfield-1240} ({@code "Contains anywhere..."}) and {@code textfield-1151}
 * ({@code "Exact match..."}). EXACT_MATCH's full type→commit→row-count-change cycle is proven
 * live (see {@code GridColumnMenu.applyFilter}'s history); CONTAINS's own live exercise is
 * covered by {@code GridColumnMenuSmokeVerification}'s dedicated "Contains-anywhere (partial
 * match)" step.
 */
public enum FilterMatchMode {

    CONTAINS("Contains anywhere..."),
    EXACT("Exact match...");

    private final String placeholderText;

    FilterMatchMode(String placeholderText) { this.placeholderText = placeholderText; }

    public String placeholderText() { return placeholderText; }
}
