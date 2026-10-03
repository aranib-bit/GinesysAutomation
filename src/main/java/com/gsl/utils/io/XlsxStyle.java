package com.gsl.utils.io;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable, domain-free description of one cell style for {@link XlsxBook} — colours are plain
 * {@code "RRGGBB"} hex strings so callers never import POI colour types. Value-equality makes it
 * the cache key {@link XlsxBook#style(XlsxStyle)} dedups on (Excel caps distinct cell styles at
 * ~64k, so styles must be shared, not created per cell).
 */
@Value
@Builder(toBuilder = true)
public class XlsxStyle {

    public enum Align { LEFT, CENTER, RIGHT, GENERAL }

    /** Font colour as {@code RRGGBB}; {@code null} = default (black). */
    String fontRgb;
    /** Solid background as {@code RRGGBB}; {@code null} = no fill. */
    String fillRgb;
    boolean bold;
    boolean italic;
    boolean underline;
    /** Font size in points; {@code 0} = workbook default. */
    int fontSize;
    /** Excel number-format string (e.g. {@code "0.0%"}, {@code "#,##0"}); {@code null} = General. */
    String numFmt;
    boolean wrap;
    @Builder.Default Align align = Align.GENERAL;
    /** {@code true} = vertically centred, else top-aligned. */
    @Builder.Default boolean vCenter = true;
    /** Thin border on all sides in {@link #borderRgb}. */
    boolean border;
    @Builder.Default String borderRgb = "BFBFBF";
}
