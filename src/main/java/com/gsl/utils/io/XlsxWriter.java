package com.gsl.utils.io;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Write-side counterpart to {@link ExportedExcelReader} — plan §11, built as a genuinely reusable
 * utility (not a one-off inside any single report class) per the user's explicit request:
 * "Later in my framework I will require CSV reader and XLSX writer." First consumer is
 * {@code com.gsl.validation.snapshot.SnapshotValidationReporter}'s full-column data dump, but
 * nothing here is specific to that caller — any future tabular export can reuse {@link #write}.
 *
 * <p>Uses the already-present {@code org.apache.poi:poi-ooxml} dependency (no {@code pom.xml}
 * change needed — confirmed already declared). A typical GV-edit-sized export (tens to low
 * hundreds of rows, a handful of columns) lands in the tens-of-KB range regardless of actual data
 * volume — POI's zip/XML scaffolding dominates over the cell data at this scale.
 */
public final class XlsxWriter {

    private XlsxWriter() {}

    /**
     * Writes {@code header} + {@code rows} to a single-sheet {@code .xlsx} at {@code outputPath},
     * creating parent directories if needed. Every cell is written as a plain string — this is a
     * data-dump writer, not a formatted-report writer; callers needing numeric/date cell types
     * should pre-format to their desired display string before calling this.
     *
     * @param outputPath where to write the file (parent directories created if absent)
     * @param sheetName  the single sheet's name (Excel truncates/sanitizes invalid characters
     *                   itself; keep it short — Excel's own 31-character sheet-name limit applies)
     * @param header     column headers, written as row 0
     * @param rows       data rows, written starting at row 1, in order
     */
    public static void write(Path outputPath, String sheetName, String[] header, List<String[]> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            writeRow(sheet, 0, header);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet, i + 1, rows.get(i));
            }
            for (int c = 0; c < header.length; c++) {
                sheet.autoSizeColumn(c);
            }
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                wb.write(out);
            }
        }
    }

    /** Widest an auto-sized column may get, in characters — long free-text cells stay readable
     *  without stretching a sheet across several screens. */
    private static final int MAX_COLUMN_CHARS = 60;

    /**
     * Writes a multi-sheet, lightly formatted {@code .xlsx}: each {@link XlsxSheet} gets a bold,
     * filled header row, optional per-value cell fills ({@link XlsxSheet#getCellFills()}), an
     * autofilter and a frozen header, and auto-sized columns capped at {@value #MAX_COLUMN_CHARS}
     * characters. Cells are still written as plain strings (see {@link #write(Path, String, String[], List)}).
     * Mechanics only — nothing here knows what any column means.
     *
     * @param outputPath where to write the file (parent directories created if absent)
     * @param sheets     sheets in tab order; must be non-empty
     */
    public static void write(Path outputPath, List<XlsxSheet> sheets) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("At least one sheet is required");
        }
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            Map<XlsxFill, CellStyle> fillStyles = new EnumMap<>(XlsxFill.class);
            for (XlsxSheet spec : sheets) {
                writeSheet(wb, spec, headerStyle, fillStyles);
            }
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            try (OutputStream out = Files.newOutputStream(outputPath)) {
                wb.write(out);
            }
        }
    }

    private static void writeSheet(Workbook wb, XlsxSheet spec, CellStyle headerStyle,
                                   Map<XlsxFill, CellStyle> fillStyles) {
        Sheet sheet = wb.createSheet(spec.getName());
        String[] header = spec.getHeader();
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < header.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(header[c] == null ? "" : header[c]);
            cell.setCellStyle(headerStyle);
        }
        List<String[]> rows = spec.getRows() == null ? List.of() : spec.getRows();
        Map<String, XlsxFill> fills = spec.getCellFills();
        for (int i = 0; i < rows.size(); i++) {
            String[] values = rows.get(i);
            Row row = sheet.createRow(i + 1);
            for (int c = 0; c < values.length; c++) {
                Cell cell = row.createCell(c);
                String text = values[c] == null ? "" : values[c];
                cell.setCellValue(text);
                XlsxFill fill = (fills == null || !fillAppliesTo(spec, c)) ? null : fills.get(text);
                if (fill != null) {
                    cell.setCellStyle(fillStyles.computeIfAbsent(fill, f -> fillStyle(wb, f)));
                }
            }
        }
        for (int c = 0; c < header.length; c++) {
            sheet.autoSizeColumn(c);
            if (sheet.getColumnWidth(c) > MAX_COLUMN_CHARS * 256) {
                sheet.setColumnWidth(c, MAX_COLUMN_CHARS * 256);
            }
        }
        if (spec.isAutoFilter() && header.length > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rows.size()), 0, header.length - 1));
        }
        if (spec.isFreezeHeader()) {
            sheet.createFreezePane(0, 1);
        }
    }

    private static boolean fillAppliesTo(XlsxSheet spec, int column) {
        int[] cols = spec.getFillColumns();
        if (cols == null || cols.length == 0) return true;
        for (int c : cols) {
            if (c == column) return true;
        }
        return false;
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle fillStyle(Workbook wb, XlsxFill fill) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(fill.color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void writeRow(Sheet sheet, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c] == null ? "" : values[c]);
        }
    }
}
