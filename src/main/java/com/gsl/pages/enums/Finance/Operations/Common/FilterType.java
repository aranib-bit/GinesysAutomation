package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * The grid-column filter UI mechanism a given {@link ColumnDataType} maps to.
 *
 * <p>Modelled here as data only — no page-object method yet drives an actual filter-menu
 * interaction. This exists so "filter mechanism differs per data type" is captured
 * explicitly (per {@link ColumnDataType#defaultFilterType()}) instead of being left
 * implicit, ahead of a future pass that wires up live filter behavior.
 */
public enum FilterType {
    TEXT_CONTAINS,
    DATE_RANGE,
    NUMBER_RANGE,
    DROPDOWN_SELECT,
    CHECKBOX_TOGGLE
}
