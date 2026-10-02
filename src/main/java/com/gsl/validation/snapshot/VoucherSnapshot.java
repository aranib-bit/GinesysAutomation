package com.gsl.validation.snapshot;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A full BEFORE or AFTER capture of every active table for one document — doc §14, plan §1.
 * Keyed by table name (declared order preserved, matching the persistence map's iteration order,
 * so the report renders tables in a stable, predictable sequence).
 */
public class VoucherSnapshot {

    private final Map<String, TableSnapshot> tables;
    private final LocalDateTime capturedAt;

    /** Defensively copies {@code tables} (order preserved); {@code null} becomes empty. Stamps
     *  {@link #capturedAt} as "now". */
    public VoucherSnapshot(Map<String, TableSnapshot> tables) {
        this.tables = tables == null ? Collections.emptyMap() : new LinkedHashMap<>(tables);
        this.capturedAt = LocalDateTime.now();
    }

    /** This snapshot's capture for {@code tableName}, or {@code null} if that table wasn't active. */
    public TableSnapshot getTable(String tableName) {
        return tables.get(tableName);
    }

    /** Every captured table, keyed by table name, in declared persistence-map order. */
    public Map<String, TableSnapshot> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    /** When this snapshot was taken (used for report display, not comparison). */
    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
