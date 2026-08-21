package com.gsl.utils.io;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    private static void writeRow(Sheet sheet, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < values.length; c++) {
            row.createCell(c).setCellValue(values[c] == null ? "" : values[c]);
        }
    }
}
