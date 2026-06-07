package com.gsl.testdata;

import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads UsersList.xlsx using Apache POI directly.
 * Sheet lookup: tries "Users" (exact), then case-insensitive, then falls back to first sheet.
 * Column headers are matched case-insensitively.
 */
public final class UserCredentialLoader {

    private UserCredentialLoader() {}

    public static List<UserCredential> loadAll() {
        String path = ConfigFactory.getExcelDataConfig().usersListPath();
        Log.info("[UserCredentialLoader] Reading credentials from '{}'", path);

        try (InputStream is = UserCredentialLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                Log.error("[UserCredentialLoader] File not found on classpath: '{}'", path);
                return List.of();
            }

            try (Workbook workbook = WorkbookFactory.create(is)) {
                Sheet sheet = resolveSheet(workbook);
                List<UserCredential> credentials = parseSheet(sheet);
                Log.info("[UserCredentialLoader] Loaded {} credential(s) from sheet '{}'.",
                        credentials.size(), sheet.getSheetName());
                return credentials;
            }
        } catch (Exception e) {
            String hint = (e.getMessage() != null && e.getMessage().contains("forward slash"))
                    ? " — xlsx ZIP structure is corrupt; re-save the file in Excel/LibreOffice to repair."
                    : "";
            Log.error("[UserCredentialLoader] Failed to read '{}': {}{}", path, e.getMessage(), hint, e);
            return List.of();
        }
    }

    private static Sheet resolveSheet(Workbook workbook) {
        // 1. exact match
        Sheet sheet = workbook.getSheet("Users");
        if (sheet != null) return sheet;

        // 2. case-insensitive match
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if ("users".equalsIgnoreCase(workbook.getSheetName(i))) {
                Log.warn("[UserCredentialLoader] Sheet 'Users' not found by exact name; using '{}' (case-insensitive match).",
                        workbook.getSheetName(i));
                return workbook.getSheetAt(i);
            }
        }

        // 3. first sheet fallback
        Sheet first = workbook.getSheetAt(0);
        Log.warn("[UserCredentialLoader] No sheet named 'Users' found; falling back to first sheet: '{}'.",
                first.getSheetName());
        return first;
    }

    private static List<UserCredential> parseSheet(Sheet sheet) {
        List<UserCredential> result = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return result;

        // build column-index map (case-insensitive, strip spaces)
        Map<String, Integer> colIndex = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = fmt.formatCellValue(cell).trim().toLowerCase().replace(" ", "");
            if (!header.isEmpty()) {
                colIndex.put(header, cell.getColumnIndex());
            }
        }
        Log.info("[UserCredentialLoader] Sheet '{}' headers found: {}", sheet.getSheetName(), colIndex.keySet());

        // If userType / executionSet columns are absent, infer from sheet name
        boolean inferUiType  = !colIndex.containsKey("usertype");
        boolean inferSet1    = !colIndex.containsKey("executionset");
        if (inferUiType)  Log.warn("[UserCredentialLoader] 'userType' column not found — defaulting all rows to UI");
        if (inferSet1)    Log.warn("[UserCredentialLoader] 'executionSet' column not found — defaulting all rows to SET1");

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String username    = cellValue(row, colIndex, fmt, "username");
            String password    = cellValue(row, colIndex, fmt, "password");
            String userTypeStr = inferUiType  ? "UI"   : cellValue(row, colIndex, fmt, "usertype");
            String execSetStr  = inferSet1    ? "SET1" : cellValue(row, colIndex, fmt, "executionset");
            String groupId     = cellValue(row, colIndex, fmt, "groupid");

            if (username == null || username.isBlank()) {
                Log.debug("[UserCredentialLoader] Skipping row {} — blank username.", r + 1);
                continue;
            }
            if (userTypeStr == null || userTypeStr.isBlank()) {
                Log.warn("[UserCredentialLoader] Skipping row {} — blank userType.", r + 1);
                continue;
            }
            if (execSetStr == null || execSetStr.isBlank()) {
                Log.warn("[UserCredentialLoader] Skipping row {} — blank executionSet.", r + 1);
                continue;
            }

            try {
                UserType     userType = UserType.valueOf(userTypeStr.trim().toUpperCase());
                ExecutionSet execSet  = ExecutionSet.valueOf(execSetStr.trim().toUpperCase());
                String       gid      = (groupId == null || groupId.isBlank()) ? null : groupId.trim();
                result.add(new UserCredential(username.trim(), password, userType, execSet, gid));
            } catch (IllegalArgumentException e) {
                Log.warn("[UserCredentialLoader] Skipping row {} — unknown userType='{}' or executionSet='{}': {}",
                        r + 1, userTypeStr, execSetStr, e.getMessage());
            }
        }
        return result;
    }

    private static String cellValue(Row row, Map<String, Integer> colIndex,
                                    DataFormatter fmt, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? null : fmt.formatCellValue(cell).trim();
    }
}
