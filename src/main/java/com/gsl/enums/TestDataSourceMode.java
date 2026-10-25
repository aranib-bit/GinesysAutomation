package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Where scenario data comes from, selected with {@code testdata.source}. Replaces the scattered
 * {@code "DB".equalsIgnoreCase(...)} comparisons.
 *
 * <ul>
 *   <li>{@link #EXCEL} — scenario rows from the env's Excel workbooks.</li>
 *   <li>{@link #DB}    — scenario rows are collected from the database (needs {@code db.available=yes}).</li>
 *   <li>{@link #API}   — scenario data is generated/looked up through the application API.</li>
 * </ul>
 *
 * Independent of {@code db.available}, which only says whether the database may be used at all
 * (validation, diagnostics, DB-mode data) — see {@code com.gsl.database.DbAvailability}.
 */
public enum TestDataSourceMode {
    EXCEL, DB, API;

    /** Parses a {@code testdata.source} value case-insensitively; the error names the value and every accepted one. */
    public static TestDataSourceMode from(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (TestDataSourceMode m : values()) {
            if (m.name().equalsIgnoreCase(value)) return m;
        }
        throw new IllegalArgumentException("testdata.source='" + raw + "' is invalid — expected one of: "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")));
    }

    public boolean usesDatabaseForData() {
        return this == DB;
    }
}
