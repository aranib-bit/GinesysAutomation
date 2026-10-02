package com.gsl.utils.io;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generic mechanics for reading a header-row workbook into plain collections — no domain types, so
 * any "table in a sheet" configuration can reuse it (see the Utility Layering Convention: what the
 * values MEAN lives with the caller, e.g. {@code UiStyleBaseline}).
 *
 * <p>Each sheet becomes a list of rows; each row is a header-name → cell-text map. Header lookup is
 * case-insensitive. Cell text is read as displayed (so numbers/dates keep their formatting); a row
 * whose cells are all blank is skipped. A sheet without any rows yields an empty list.
 */
public final class ExcelSheetReader {

    private ExcelSheetReader() {}

    /** Every sheet of the workbook: sheet name (insertion order = workbook order) → rows. */
    public static Map<String, List<Map<String, String>>> readAll(InputStream in) throws IOException {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                result.put(sheet.getSheetName(), readSheet(sheet, formatter));
            }
        }
        return result;
    }

    private static List<Map<String, String>> readSheet(Sheet sheet, DataFormatter formatter) {
        List<Map<String, String>> rows = new ArrayList<>();
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) return rows;

        List<String> headers = new ArrayList<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }

        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, String> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            boolean any = false;
            for (int c = 0; c < headers.size(); c++) {
                if (headers.get(c).isEmpty()) continue;
                Cell cell = row.getCell(c);
                String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (!text.isEmpty()) any = true;
                values.put(headers.get(c), text);
            }
            if (any) rows.add(values);
        }
        return rows;
    }
}
