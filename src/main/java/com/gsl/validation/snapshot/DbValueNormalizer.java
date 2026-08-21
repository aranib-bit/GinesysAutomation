package com.gsl.validation.snapshot;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * Normalizes raw JDBI {@code mapToMap()} column values before comparison — doc §34.
 *
 * <p>Handles the value-shape mismatches that would otherwise produce false-positive
 * MUST_NOT_CHANGE failures purely from driver representation differences, not real data
 * changes:
 * <ul>
 *   <li>{@link BigDecimal} scale (e.g. {@code 900.00} vs {@code 900.0}) — compared via
 *       {@link BigDecimal#compareTo}, never {@code equals}.</li>
 *   <li>{@link Timestamp}/{@link java.util.Date}/{@link LocalDateTime}/{@link LocalDate} —
 *       all normalized to {@link LocalDateTime} for a uniform comparison surface.</li>
 *   <li>Strings — trimmed; blank and {@code null} are treated as equivalent.</li>
 * </ul>
 */
public final class DbValueNormalizer {

    private DbValueNormalizer() {}

    /** {@code true} when {@code a} and {@code b} are equal after normalization. */
    public static boolean equalsNormalized(Object a, Object b) {
        Object na = normalize(a);
        Object nb = normalize(b);
        if (na == null && nb == null) return true;
        if (na == null || nb == null) return false;
        if (na instanceof BigDecimal && nb instanceof BigDecimal) {
            return ((BigDecimal) na).compareTo((BigDecimal) nb) == 0;
        }
        return na.equals(nb);
    }

    /** Normalizes one raw value into a stable, comparable shape. */
    public static Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        if (value instanceof LocalDate ld) {
            return ld.atStartOfDay();
        }
        if (value instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        return value;
    }

    /** Best-effort coercion to {@link LocalDateTime} for timestamp-shaped comparisons; {@code null} if not timestamp-like. */
    public static LocalDateTime asLocalDateTime(Object value) {
        Object n = normalize(value);
        return (n instanceof LocalDateTime ldt) ? ldt : null;
    }

    /** Best-effort coercion to {@link BigDecimal} for numeric comparisons; {@code null} if not numeric. */
    public static BigDecimal asBigDecimal(Object value) {
        Object n = normalize(value);
        if (n instanceof BigDecimal bd) return bd;
        if (n instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return null;
    }

    /** Renders a normalized value for report display — never throws on odd types.
     *
     * <p>{@link BigDecimal} is rendered via {@link BigDecimal#toPlainString()}, not
     * {@code toString()} — {@code normalize()} already called {@code stripTrailingZeros()}
     * on it for comparison purposes, and a stripped round number (e.g. {@code 900.00} →
     * unscaled {@code 9}, scale {@code -2}) renders as scientific notation ({@code "9E+2"})
     * under plain {@code toString()}. {@code toPlainString()} avoids that regardless of scale,
     * without affecting the {@code compareTo}-based equality in {@link #equalsNormalized}. */
    public static String displayString(Object value) {
        Object n = normalize(value);
        if (n == null) return "—";
        if (n instanceof BigDecimal bd) return bd.toPlainString();
        return n.toString();
    }

    public static boolean isBlank(Object value) {
        return normalize(value) == null;
    }

    /** Null-safe equality helper used outside the BigDecimal/timestamp-aware path above. */
    public static boolean rawEquals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /** Case-insensitive column lookup in a raw JDBI {@code mapToMap()} row — Postgres/JDBI column
     *  keys aren't guaranteed to match a caller's exact case. Returns {@code null} if absent. */
    public static Object getIgnoreCase(Map<String, Object> row, String column) {
        if (row == null || column == null) return null;
        Object direct = row.get(column);
        if (direct != null) return direct;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(column)) return e.getValue();
        }
        return null;
    }
}
