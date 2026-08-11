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
 * Sheet lookup, in order:
 * <ol>
 *     <li>A single combined "Users" sheet (exact or case-insensitive) — legacy/back-compat shape;
 *     rows default to {@link UserType#UI} only if no {@code userType} column is present.</li>
 *     <li>Typed per-userType sheets — "UIUsers" and/or "APIUsers" (case-insensitive), each
 *     contributing its own rows and merged into one list. Rows without an explicit
 *     {@code userType} column default to the type implied by their OWN sheet name (UI for
 *     "UIUsers", API for "APIUsers") instead of every unmatched sheet silently defaulting to UI.
 *     Note: as of writing, this workbook's "APIUsers" sheet is a differently-shaped
 *     {@code APIKey}/{@code Usage} table (static keys, not login credentials) and contributes no
 *     rows here — it has no {@code username} column, so every row is skipped as blank-username.
 *     Login-style accounts intended as {@link UserType#API} (e.g. {@code webapi1/2/3}) currently
 *     live in "UIUsers" without a {@code userType} column, so they still default to UI here; that
 *     is a data-shape gap in the workbook itself, not something sheet selection alone can fix —
 *     it needs an explicit {@code userType} column added to "UIUsers", or those rows moved to a
 *     properly-shaped API-credentials sheet.</li>
 *     <li>First sheet in the workbook, defaulting to UI, with a loud warning — last resort only,
 *     for workbooks that use neither of the above shapes.</li>
 * </ol>
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
                List<UserCredential> credentials = new ArrayList<>();
                for (SheetResolution resolution : resolveSheets(workbook)) {
                    List<UserCredential> fromSheet = parseSheet(resolution.sheet, resolution.defaultUserType);
                    credentials.addAll(fromSheet);
                    Log.info("[UserCredentialLoader] Loaded {} credential(s) from sheet '{}' (default userType={}).",
                            fromSheet.size(), resolution.sheet.getSheetName(), resolution.defaultUserType);
                }
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

    /** Pairs a resolved sheet with the {@link UserType} its rows should default to when the
     *  sheet has no explicit {@code userType} column. */
    private static final class SheetResolution {
        final Sheet sheet;
        final UserType defaultUserType;
        SheetResolution(Sheet sheet, UserType defaultUserType) {
            this.sheet = sheet;
            this.defaultUserType = defaultUserType;
        }
    }

    private static List<SheetResolution> resolveSheets(Workbook workbook) {
        // 1. Single combined "Users" sheet — legacy/back-compat shape.
        Sheet usersSheet = findSheetByName(workbook, "Users");
        if (usersSheet != null) {
            return List.of(new SheetResolution(usersSheet, UserType.UI));
        }

        // 2. Typed per-userType sheets — "UIUsers"/"APIUsers". Previously only ever the FIRST
        // sheet in the workbook was read (a plain resolveSheet() call with no "Users" match fell
        // back to workbook.getSheetAt(0)), so if "APIUsers" happened to be sheet 2+, its rows
        // (e.g. dedicated webapi1/2/3 accounts) were never loaded at all.
        List<SheetResolution> typed = new ArrayList<>();
        Sheet uiSheet = findSheetByName(workbook, "UIUsers");
        if (uiSheet != null) typed.add(new SheetResolution(uiSheet, UserType.UI));
        Sheet apiSheet = findSheetByName(workbook, "APIUsers");
        if (apiSheet != null) typed.add(new SheetResolution(apiSheet, UserType.API));
        if (!typed.isEmpty()) return typed;

        // 3. First sheet fallback — last resort only.
        Sheet first = workbook.getSheetAt(0);
        Log.warn("[UserCredentialLoader] No 'Users', 'UIUsers', or 'APIUsers' sheet found; falling back "
                + "to first sheet '{}'. Rows without an explicit userType column will default to UI, which "
                + "may silently misclassify dedicated API/WEBAPI accounts as interactive UI accounts.",
                first.getSheetName());
        return List.of(new SheetResolution(first, UserType.UI));
    }

    private static Sheet findSheetByName(Workbook workbook, String name) {
        Sheet exact = workbook.getSheet(name);
        if (exact != null) return exact;
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (name.equalsIgnoreCase(workbook.getSheetName(i))) {
                return workbook.getSheetAt(i);
            }
        }
        return null;
    }

    private static List<UserCredential> parseSheet(Sheet sheet, UserType defaultUserType) {
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

        // If userType / executionSet columns are absent, infer from the sheet's own default
        // (see resolveSheets()) rather than blindly assuming UI regardless of which sheet this is.
        boolean inferUserType = !colIndex.containsKey("usertype");
        boolean inferSet1     = !colIndex.containsKey("executionset");
        if (inferUserType) Log.warn("[UserCredentialLoader] Sheet '{}': 'userType' column not found — defaulting all rows to {}",
                sheet.getSheetName(), defaultUserType);
        if (inferSet1)      Log.warn("[UserCredentialLoader] Sheet '{}': 'executionSet' column not found — defaulting all rows to SET1",
                sheet.getSheetName());

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String username    = cellValue(row, colIndex, fmt, "username");
            String password    = cellValue(row, colIndex, fmt, "password");
            String userTypeStr = inferUserType ? defaultUserType.name() : cellValue(row, colIndex, fmt, "usertype");
            String execSetStr  = inferSet1     ? "SET1" : cellValue(row, colIndex, fmt, "executionset");
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
