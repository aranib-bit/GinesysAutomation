package com.gsl.utils.io;

import com.gsl.utils.Log;
import org.apache.poi.ss.usermodel.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a workbook the browser just downloaded from disk (as opposed to {@code XlsxReader}/
 * {@code UserCredentialLoader}, which read bundled test-data fixtures from the classpath).
 * Sheet lookup falls back to the first sheet; column headers are matched case-insensitively —
 * same conventions as {@link com.gsl.testdata.UserCredentialLoader#loadAll()}, the closest
 * existing template, adapted for an arbitrary filesystem path instead of a fixed resource name.
 */
public final class ExportedExcelReader {

    private ExportedExcelReader() {}

    /** One row of the exported sheet as a case-insensitive header -> cell-value map. */
    public static List<Map<String, String>> readRows(Path file) {
        List<Map<String, String>> result = new ArrayList<>();

        try (InputStream is = new FileInputStream(file.toFile());
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return result;
            }

            Map<Integer, String> headerByIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = fmt.formatCellValue(cell).trim();
                if (!header.isEmpty()) {
                    headerByIndex.put(cell.getColumnIndex(), header);
                }
            }
            Log.info("[ExportedExcelReader] '{}' headers: {}", file, headerByIndex.values());

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowMap = new HashMap<>();
                for (Map.Entry<Integer, String> h : headerByIndex.entrySet()) {
                    Cell cell = row.getCell(h.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    rowMap.put(h.getValue().toLowerCase(), cell == null ? null : fmt.formatCellValue(cell).trim());
                }
                if (!rowMap.isEmpty()) {
                    result.add(rowMap);
                }
            }
        } catch (Exception e) {
            Log.error("[ExportedExcelReader] Failed to read '{}': {}", file, e.getMessage(), e);
        }
        return result;
    }

    /** Case-insensitive lookup of a column value from a row map returned by {@link #readRows}. */
    public static String get(Map<String, String> row, String columnName) {
        return row.get(columnName.toLowerCase());
    }
}
