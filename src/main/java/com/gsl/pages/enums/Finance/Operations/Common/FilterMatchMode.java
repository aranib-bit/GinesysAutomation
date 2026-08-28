package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * The two text-match modes offered in an ExtJS grid column's "Filters" flyout for a
 * {@code STRING}-typed column ({@link ColumnDataType#STRING}, {@link FilterType#TEXT_CONTAINS}):
 * a partial/"contains anywhere" match and an exact match. Carries the flyout's placeholder text
 * for each input, used to build that input's locator.
 *
 * <p>[VERIFY step 0]: placeholder text confirmed by eye from a live screenshot of the app's
 * column-header menu (General Voucher list, hovering a column header) — not yet confirmed via
 * DevTools whether this is a real HTML5 {@code placeholder} attribute or an ExtJS synthetic
 * {@code emptyText} value. See {@code GridColumnMenu}'s Javadoc for how this affects the input
 * locator/typing strategy.
 */
public enum FilterMatchMode {

    CONTAINS("Contains anywhere..."),
    EXACT("Exact match...");

    private final String placeholderText;

    FilterMatchMode(String placeholderText) { this.placeholderText = placeholderText; }

    public String placeholderText() { return placeholderText; }
}
