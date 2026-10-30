package com.gsl.api.model.audit;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One audit-history row as the Audit API returns it: a full snapshot of the audited table row at one version. Column names have
 * irregular casing ({@code versioN_SEQ}, {@code releasE_STATUS}), so every lookup is case-insensitive.
 */
public final class AuditRow {

    /** Columns that change on every version and so never count as a data change when two versions are compared. */
    public static final Set<String> VOLATILE = Set.of(
            "version_seq", "action", "login_user", "transaction_date", "transaction_id", "action_subtype");

    private final Map<String, Object> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public AuditRow(Map<String, Object> raw) {
        if (raw != null) values.putAll(raw);
    }

    public Object get(String column) { return values.get(column); }

    public String str(String column) {
        Object v = values.get(column);
        return v == null ? null : String.valueOf(v).trim();
    }

    public BigDecimal num(String column) {
        Object v = values.get(column);
        if (v == null) return null;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int version() {
        BigDecimal v = num("versioN_SEQ");
        return v == null ? 0 : v.intValue();
    }

    public String action() { return str("action"); }

    public String transactionId() { return str("transactioN_ID"); }

    public Map<String, Object> asMap() { return Collections.unmodifiableMap(values); }

    /** Data columns whose value differs between {@code before} and {@code this}, ignoring {@link #VOLATILE} ones; value is {@code {before, after}}. */
    public Map<String, Object[]> changedFrom(AuditRow before) {
        Map<String, Object[]> changed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(values.keySet());
        keys.addAll(before.values.keySet());
        for (String k : keys) {
            if (VOLATILE.contains(k.toLowerCase())) continue;
            Object a = before.values.get(k);
            Object b = values.get(k);
            if (!same(a, b)) changed.put(k, new Object[]{a, b});
        }
        return changed;
    }

    private static boolean same(Object a, Object b) {
        if (a == null || b == null) return a == b;
        try {
            return new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) == 0;
        } catch (NumberFormatException e) {
            return String.valueOf(a).trim().equals(String.valueOf(b).trim());
        }
    }
}
