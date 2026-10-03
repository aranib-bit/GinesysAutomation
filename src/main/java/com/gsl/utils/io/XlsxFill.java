package com.gsl.utils.io;

import org.apache.poi.ss.usermodel.IndexedColors;

/**
 * Domain-free palette of cell background fills {@link XlsxWriter} can apply to a cell by value —
 * see {@link XlsxSheet#getCellFills()}. Keeps POI's colour types out of every caller's imports.
 */
public enum XlsxFill {
    GREEN(IndexedColors.LIGHT_GREEN),
    RED(IndexedColors.ROSE),
    AMBER(IndexedColors.LIGHT_YELLOW),
    GREY(IndexedColors.GREY_25_PERCENT),
    BLUE(IndexedColors.PALE_BLUE);

    final IndexedColors color;

    XlsxFill(IndexedColors color) {
        this.color = color;
    }
}
