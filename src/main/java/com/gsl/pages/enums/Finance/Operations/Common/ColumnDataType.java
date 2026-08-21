package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Data type of a voucher list-grid column, as declared by an {@link IVoucherListColumn}
 * constant. Drives {@link #defaultFilterType()} — the grid-column filter mechanism a
 * column needs differs by its data type (free-text contains, date-range picker,
 * numeric-range, dropdown-select, checkbox-toggle) — modelled here as data only; no
 * live filter-interaction methods exist yet (structural/enum pass only).
 */
public enum ColumnDataType {
    STRING(FilterType.TEXT_CONTAINS),
    DATE(FilterType.DATE_RANGE),
    DECIMAL(FilterType.NUMBER_RANGE),
    INTEGER(FilterType.NUMBER_RANGE),
    ENUM_STATUS(FilterType.DROPDOWN_SELECT),
    BOOLEAN(FilterType.CHECKBOX_TOGGLE);

    private final FilterType defaultFilterType;

    ColumnDataType(FilterType defaultFilterType) {
        this.defaultFilterType = defaultFilterType;
    }

    /** The filter UI mechanism this data type is expected to use once filtering is implemented. */
    public FilterType defaultFilterType() {
        return defaultFilterType;
    }
}
