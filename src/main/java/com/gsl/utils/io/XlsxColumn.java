package com.gsl.utils.io;

import lombok.Builder;
import lombok.Value;

/**
 * Column spec for {@link XlsxBook#writeTable}: header, width and how each cell's value is typed
 * and formatted. Values are dispatched on their Java type (numbers stay numeric, {@code
 * LocalDateTime} becomes a real date, {@link XlsxLink} a hyperlink, anything else text), so the
 * {@link Kind} only chooses the number format and default alignment.
 */
@Value
@Builder
public class XlsxColumn {

    public enum Kind {
        TEXT(null), INT("#,##0"), DECIMAL1("#,##0.0"), DECIMAL2("#,##0.00"), PERCENT("0.0%"),
        DATETIME("yyyy-mm-dd hh:mm:ss"), TIME("hh:mm:ss"), LINK(null);

        final String format;

        Kind(String format) {
            this.format = format;
        }
    }

    String header;
    /** Width in characters (Excel units / 256 internally). */
    @Builder.Default int width = 14;
    @Builder.Default Kind kind = Kind.TEXT;
    /** Wrap long text in this column. */
    boolean wrap;
    /** Centre-align (status, flags, run #). */
    boolean center;

    public static XlsxColumn of(String header, int width) {
        return XlsxColumn.builder().header(header).width(width).build();
    }

    public static XlsxColumn of(String header, int width, Kind kind) {
        return XlsxColumn.builder().header(header).width(width).kind(kind).build();
    }

    public static XlsxColumn wrapped(String header, int width) {
        return XlsxColumn.builder().header(header).width(width).wrap(true).build();
    }
}
