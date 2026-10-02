package com.gsl.utils;

/**
 * Centralised, single source of truth for expected page-styling (font-size/font-family) values
 * shared across finance transaction pages/modules — deliberately NOT duplicated per page/module.
 * Confirmed via DevTools inspection that the underlying CSS rules are duplicated identically
 * across {@code Fin.css}/{@code Adm.css}/{@code charts.css}, i.e. genuinely shared, not a
 * per-module override.
 *
 * <p>Every page's own styling-regression check should reference these SAME constants, not define
 * its own copy — only that way does a real cross-page styling drift actually get caught. A
 * per-page duplicate (even with today's identical literal value) would silently keep passing
 * against its own private expectation even if that one page later drifts from the rest of the
 * app.
 */
public enum PageStylingSpec {

    /** The breadcrumb label (e.g. "Finance &gt; Operations &gt; Vouchers &gt;") shown above any
     *  module's page header. Read via {@link PageHeaderUtils#getBreadcrumbFont()}. */
    BREADCRUMB("13px", "Segoe UI Light, Open Sans"),

    /** Any Add/Edit form's own {@code winTitleType1} window title. Read via
     *  {@link PageHeaderUtils#getWindowTitleFont()}. */
    WINDOW_TITLE("25px", "Segoe UI Light, Open Sans"),

    /** Any module's read-only ExtJS displayfield (e.g. General Voucher's "Voucher No.:" field) —
     *  shares the same {@code x-form-display-field-default} styling rule regardless of which
     *  field/module it appears on. */
    READONLY_DISPLAY_FIELD("12px", "Segoe UI, Open Sans"),
    ;

    private final String fontSize;
    private final String fontFamily;

    PageStylingSpec(String fontSize, String fontFamily) {
        this.fontSize = fontSize;
        this.fontFamily = fontFamily;
    }

    public String fontSize()   { return fontSize; }
    public String fontFamily() { return fontFamily; }

    /** Same {@code "<size>|<family>"} shape {@code SeleniumUtils#getComputedFont} returns, so
     *  callers can compare directly without building the string themselves. */
    public String expectedFont() { return fontSize + "|" + fontFamily; }
}
