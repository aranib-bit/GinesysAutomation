package com.gsl.utils.io;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * One sheet of a multi-sheet workbook for {@link XlsxWriter#write(java.nio.file.Path, List)} —
 * pure data (strings and collections only, no domain types), so any report can describe a sheet
 * without {@link XlsxWriter} knowing what the rows mean.
 */
@Getter
@Builder
public class XlsxSheet {

    /** Sheet name (Excel's 31-character limit and invalid-character rules apply; keep it short). */
    private final String name;

    /** Column headers, written as a bold, filled row 0. */
    private final String[] header;

    /** Data rows, written from row 1 in order; {@code null} cells are written as blank. */
    private final List<String[]> rows;

    /** Cells whose exact text matches a key get that background fill (e.g. {@code "PASS"} →
     *  {@link XlsxFill#GREEN}); {@code null}/empty means no conditional fills on this sheet. */
    private final Map<String, XlsxFill> cellFills;

    /** Column indexes the {@link #cellFills} apply to; {@code null}/empty means every column. */
    private final int[] fillColumns;

    /** Whether to add an autofilter over the header row. */
    @Builder.Default
    private final boolean autoFilter = true;

    /** Whether to freeze the header row so it stays visible while scrolling. */
    @Builder.Default
    private final boolean freezeHeader = true;
}
