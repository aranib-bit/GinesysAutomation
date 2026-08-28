package com.gsl.utils.io;

import com.gsl.utils.Log;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Reads one "optional" column from an Excel sheet — a column {@code XlsxReader}/ZeroCell can't
 * safely map via {@code @Column} because it would hard-fail whenever that header is absent from
 * an existing sheet — and merges its per-row values into an already-loaded list of domain
 * objects. Generic mechanics only (open workbook, resolve header indices by name, build a
 * lookup keyed by 1-2 columns, apply a caller-supplied setter to each match); zero knowledge of
 * what any sheet/column/domain object means — see {@code docs/conventions/utility-layering.md}.
 *
 * <p>Extracted out of three near-identical private methods that had accumulated independently in
 * {@code ExcelApVoucherDataSource} ({@code readOptionalOnAccountConfirm}, keyed by one column;
 * {@code readOptionalDeductTdsConfirm}, keyed by two) and {@code ExcelGeneralVoucherDataSource}
 * ({@code readOptionalExpectedGlType}, keyed by two) — same mechanic copy-pasted three times with
 * only the sheet/column names, key shape, and setter differing. This is a pure extraction: the
 * lookup-key composition below intentionally preserves those methods' exact (slightly
 * inconsistent — only the first key is lowercased, not the second) matching behavior rather than
 * "fixing" it, so behavior is unchanged before/after.
 *
 * <p>If the sheet or the value column is absent, both methods are a silent no-op (mirroring the
 * original methods) — the whole point is to tolerate an optional column that doesn't exist yet
 * in older Excel fixtures. Any other failure (unreadable file, malformed sheet) is caught and
 * logged as a warning, never thrown — an optional-column read should never fail a test run.
 */
public final class OptionalColumnMerger {

    private OptionalColumnMerger() {}

    /**
     * Reads {@code valueColumnHeader} from {@code sheetName}, keyed by a single column
     * (e.g. {@code "TestCaseID"}), and merges matched values into {@code targetList} via
     * {@code valueSetter}.
     *
     * @param excelPath         path to the workbook
     * @param sheetName         sheet to read
     * @param keyColumnHeader   header of the column identifying each sheet row (e.g. TestCaseID)
     * @param valueColumnHeader header of the optional column to read
     * @param targetList        already-loaded domain objects to merge values into
     * @param keyExtractor      returns a target object's own key value, for matching against the sheet
     * @param valueSetter       applies a matched value to a target object
     * @param logTag            caller identifier for log messages, e.g. {@code "[ExcelApVoucherDataSource]"}
     */
    public static <T> void mergeByOneKey(
            String excelPath, String sheetName,
            String keyColumnHeader, String valueColumnHeader,
            List<T> targetList,
            Function<T, String> keyExtractor,
            BiConsumer<T, String> valueSetter,
            String logTag) {

        Map<String, String> valueByKey = readColumn(excelPath, sheetName, valueColumnHeader,
                new String[] {keyColumnHeader}, logTag);
        if (valueByKey == null) {
            return;
        }

        targetList.forEach(target -> {
            String key = keyExtractor.apply(target);
            if (key != null) {
                String value = valueByKey.get(key.toLowerCase());
                if (value != null) {
                    valueSetter.accept(target, value);
                }
            }
        });

        Log.debug("{} '{}' merged for {} row(s)", logTag, valueColumnHeader, valueByKey.size());
    }

    /**
     * Reads {@code valueColumnHeader} from {@code sheetName}, keyed by two columns
     * (e.g. {@code "TestCaseID"} + {@code "LineNo"}), and merges matched values into
     * {@code targetList} via {@code valueSetter}.
     *
     * @param key1Extractor returns a target object's first key value (lowercased before matching)
     * @param key2Extractor returns a target object's second key value (matched as-is, not
     *                      lowercased — preserved verbatim from the original methods)
     * @see #mergeByOneKey for the other parameters
     */
    public static <T> void mergeByTwoKeys(
            String excelPath, String sheetName,
            String key1ColumnHeader, String key2ColumnHeader, String valueColumnHeader,
            List<T> targetList,
            Function<T, String> key1Extractor, Function<T, String> key2Extractor,
            BiConsumer<T, String> valueSetter,
            String logTag) {

        Map<String, String> valueByKey = readColumn(excelPath, sheetName, valueColumnHeader,
                new String[] {key1ColumnHeader, key2ColumnHeader}, logTag);
        if (valueByKey == null) {
            return;
        }

        targetList.forEach(target -> {
            String key1 = key1Extractor.apply(target);
            String key2 = key2Extractor.apply(target);
            if (key1 != null && key2 != null) {
                String value = valueByKey.get(key1.toLowerCase() + "|" + key2);
                if (value != null) {
                    valueSetter.accept(target, value);
                }
            }
        });

        Log.debug("{} '{}' merged for {} row(s)", logTag, valueColumnHeader, valueByKey.size());
    }

    /**
     * Opens {@code excelPath} via raw POI (bypassing XlsxReader, which would hard-fail on a
     * missing {@code @Column} header) and builds a lookup of {@code valueColumnHeader}'s values,
     * keyed by 1-2 columns joined with {@code "|"} (first key lowercased, second — if present —
     * left as-is). Returns {@code null} (silent no-op signal, no further logging by the caller)
     * if the sheet or the value column isn't present, or if reading fails for any other reason
     * (logged here as a warning) — matches the original methods' behavior of returning
     * immediately without a trailing "merged for N rows" summary in every no-op/failure case.
     */
    private static Map<String, String> readColumn(
            String excelPath, String sheetName, String valueColumnHeader,
            String[] keyColumnHeaders, String logTag) {

        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                Log.debug("{} Sheet '{}' not found — skipping '{}' read", logTag, sheetName, valueColumnHeader);
                return null;
            }

            Row header = sheet.getRow(0);
            if (header == null) {
                return null;
            }

            int[] keyCols = new int[keyColumnHeaders.length];
            java.util.Arrays.fill(keyCols, -1);
            int valueCol = -1;
            for (Cell cell : header) {
                String h = cell.getStringCellValue().trim();
                for (int i = 0; i < keyColumnHeaders.length; i++) {
                    if (keyColumnHeaders[i].equalsIgnoreCase(h)) {
                        keyCols[i] = cell.getColumnIndex();
                    }
                }
                if (valueColumnHeader.equalsIgnoreCase(h)) {
                    valueCol = cell.getColumnIndex();
                }
            }

            if (valueCol == -1) {
                Log.debug("{} '{}' column not present in sheet '{}' — no-op",
                        logTag, valueColumnHeader, sheetName);
                return null;
            }

            Map<String, String> valueByKey = new HashMap<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String[] keyParts = new String[keyCols.length];
                boolean allKeysPresent = true;
                for (int i = 0; i < keyCols.length; i++) {
                    keyParts[i] = keyCols[i] >= 0 ? cellStr(row.getCell(keyCols[i])) : null;
                    if (keyParts[i] == null) {
                        allKeysPresent = false;
                    }
                }
                String value = cellStr(row.getCell(valueCol));
                if (allKeysPresent && value != null) {
                    valueByKey.put(composeKey(keyParts), value);
                }
            }
            return valueByKey;

        } catch (Exception e) {
            Log.warn("{} Could not read '{}' column — skipping ({})", logTag, valueColumnHeader, e.getMessage());
            return null;
        }
    }

    /** First key lowercased; any subsequent key left as-is — matches the original methods' behavior. */
    private static String composeKey(String[] keyParts) {
        StringBuilder sb = new StringBuilder(keyParts[0].toLowerCase());
        for (int i = 1; i < keyParts.length; i++) {
            sb.append('|').append(keyParts[i]);
        }
        return sb.toString();
    }

    /** Returns trimmed cell string, or {@code null} if blank/missing. */
    private static String cellStr(Cell cell) {
        if (cell == null) return null;
        String val;
        if (cell.getCellType() == CellType.STRING) {
            val = cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            val = String.valueOf((long) cell.getNumericCellValue());
        } else {
            val = cell.toString();
        }
        return val == null || val.isBlank() ? null : val.trim();
    }
}
