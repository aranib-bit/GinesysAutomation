package com.gsl.testdata;

import com.gsl.config.ConfigFactory;
import com.gsl.config.Secrets;
import com.gsl.utils.Log;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Column headers are matched case-insensitively. Columns: {@code username}, {@code password}, {@code usertype} (e.g. {@code [UI, WEBAPI]}, see
 * {@link #parseTypes}), {@code logintype} ({@code ERP} default or {@code SSO}), {@code executionset}, {@code groupid}.
 */
public final class UserCredentialLoader {

    private UserCredentialLoader() {}

    /**
     * Loads every {@link UserCredential} from the configured {@code UsersList.xlsx} (path from
     * {@code ConfigFactory.getExcelDataConfig().usersListPath()}), per the sheet-resolution order
     * documented on this class. Returns {@link List#of()} (never {@code null}) if the file is
     * missing on the classpath or fails to parse — logged as an error either way, not thrown, so
     * a bad workbook surfaces as "no users configured" (via {@link UserPool#hasUsersOfType}) at
     * the point of use rather than crashing suite startup.
     */
    public static List<UserCredential> loadAll() {
        String path = ConfigFactory.getExcelDataConfig().usersListPath();
        Log.info("[UserCredentialLoader] Reading credentials from '{}'", path);

        try (InputStream is = UserCredentialLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                Log.error("[UserCredentialLoader] File not found on classpath: '{}'", path);
                return List.of();
            }

            try (Workbook workbook = WorkbookFactory.create(is)) {
                return fromWorkbook(workbook);
            }
        } catch (Exception e) {
            String hint = (e.getMessage() != null && e.getMessage().contains("forward slash"))
                    ? " — xlsx ZIP structure is corrupt; re-save the file in Excel/LibreOffice to repair."
                    : "";
            Log.error("[UserCredentialLoader] Failed to read '{}': {}{}", path, e.getMessage(), hint, e);
            return List.of();
        }
    }

    /** Every credential of an already-open workbook, per the sheet-resolution order documented on this class (also the entry point for unit tests). */
    public static List<UserCredential> fromWorkbook(Workbook workbook) {
        List<UserCredential> credentials = new ArrayList<>();
        for (SheetResolution resolution : resolveSheets(workbook)) {
            List<UserCredential> fromSheet = parseSheet(resolution.sheet, resolution.defaultUserType);
            credentials.addAll(fromSheet);
            Log.info("[UserCredentialLoader] Loaded {} credential(s) from sheet '{}' (default userType={}).",
                    fromSheet.size(), resolution.sheet.getSheetName(), resolution.defaultUserType);
        }
        return credentials;
    }

    /**
     * Parses a {@code usertype} cell: {@code [UI, WEBAPI]} with brackets, or a bare value ({@code UI}). The legacy value {@code API} reads as
     * {@code WEBAPI}. A user has ONE type, except {@code UI} + {@code WEBAPI} together (either order), the only allowed combination because those two
     * uses share one login; {@code PERF} and {@code PUBLICAPI} are always alone.
     *
     * @throws IllegalArgumentException naming the row, for an unknown value, a duplicate, an empty entry or bracket pair, an unclosed bracket, or a
     *                                  combination other than UI + WEBAPI
     */
    public static Set<UserType> parseTypes(String cell, int rowNumber) {
        String where = "row " + rowNumber + ": usertype '" + cell + "'";
        String raw = cell == null ? "" : cell.trim();
        if (raw.startsWith("[") || raw.endsWith("]")) {
            if (!(raw.startsWith("[") && raw.endsWith("]"))) throw new IllegalArgumentException(where + " has an unmatched bracket");
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        if (raw.isEmpty()) throw new IllegalArgumentException(where + " is empty");
        EnumSet<UserType> types = EnumSet.noneOf(UserType.class);
        for (String part : raw.split(",", -1)) {
            String name = part.trim().toUpperCase(java.util.Locale.ROOT);
            if (name.isEmpty()) throw new IllegalArgumentException(where + " has an empty entry");
            if (name.equals("API")) name = UserType.WEBAPI.name();
            UserType type;
            try {
                type = UserType.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(where + " has unknown type '" + part.trim() + "' (allowed: UI, WEBAPI, PUBLICAPI, PERF)");
            }
            if (!types.add(type)) throw new IllegalArgumentException(where + " lists " + type + " twice");
        }
        if (types.size() > 1 && !types.equals(EnumSet.of(UserType.UI, UserType.WEBAPI))) {
            throw new IllegalArgumentException(where + " combines " + types + "; the only allowed combination is [UI, WEBAPI]");
        }
        return types;
    }

    /** Parses a {@code logintype} cell: blank means {@link LoginType#ERP}. @throws IllegalArgumentException for an unknown value */
    public static LoginType parseLoginType(String cell, int rowNumber) {
        if (cell == null || cell.isBlank()) return LoginType.ERP;
        try {
            return LoginType.valueOf(cell.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("row " + rowNumber + ": logintype '" + cell + "' is unknown (allowed: ERP, SSO)");
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

    /** Picks which sheet(s) to read and each one's default {@link UserType}, per the 3-tier order documented on this class. */
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
        if (apiSheet != null) typed.add(new SheetResolution(apiSheet, UserType.WEBAPI));
        if (!typed.isEmpty()) return typed;

        // 3. First sheet fallback — last resort only.
        Sheet first = workbook.getSheetAt(0);
        Log.warn("[UserCredentialLoader] No 'Users', 'UIUsers', or 'APIUsers' sheet found; falling back "
                + "to first sheet '{}'. Rows without an explicit userType column will default to UI, which "
                + "may silently misclassify dedicated API/WEBAPI accounts as interactive UI accounts.",
                first.getSheetName());
        return List.of(new SheetResolution(first, UserType.UI));
    }

    /** Case-insensitive sheet lookup by name; returns {@code null} if no sheet matches. */
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

    /**
     * Parses one resolved sheet into {@link UserCredential}s, matching column headers
     * case-insensitively (spaces stripped). Rows missing a {@code username} are skipped
     * (debug-logged); rows with a blank {@code userType} or {@code executionSet} — after
     * applying {@code defaultUserType}/SET1 inference for sheets without those columns — or an
     * unrecognized enum value are skipped with a warning rather than failing the whole load.
     */
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
                Set<UserType> types    = inferUserType ? Set.of(defaultUserType) : parseTypes(userTypeStr, r + 1);
                LoginType     login    = parseLoginType(cellValue(row, colIndex, fmt, "logintype"), r + 1);
                ExecutionSet  execSet  = ExecutionSet.valueOf(execSetStr.trim().toUpperCase());
                String        gid      = (groupId == null || groupId.isBlank()) ? null : groupId.trim();
                result.add(new UserCredential(username.trim(), resolvePassword(username.trim(), password), types, login, execSet, gid));
            } catch (IllegalArgumentException e) {
                Log.error("[UserCredentialLoader] Skipping row {} ({}): {}", r + 1, username.trim(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Keeps passwords out of committed workbooks. Order: a per-user secret
     * ({@code user.<username>.password}), then the workbook's password cell, then the shared
     * default secret ({@code user.default.password}). Secrets come from {@link Secrets}
     * (-D, environment variable, or the environment's gitignored secrets.local.properties).
     */
    private static String resolvePassword(String username, String cellPassword) {
        return Secrets.find("user." + username + ".password")
                .or(() -> cellPassword == null || cellPassword.isBlank()
                        ? Secrets.find("user.default.password") : java.util.Optional.of(cellPassword))
                .orElse(cellPassword);
    }

    /** Reads {@code colName}'s cell value from {@code row} as trimmed text, or {@code null} if the column is absent or the cell is blank. */
    private static String cellValue(Row row, Map<String, Integer> colIndex,
                                    DataFormatter fmt, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        Cell cell = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? null : fmt.formatCellValue(cell).trim();
    }
}
