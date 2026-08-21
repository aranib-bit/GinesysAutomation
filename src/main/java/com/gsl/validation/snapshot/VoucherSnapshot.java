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

    public VoucherSnapshot(Map<String, TableSnapshot> tables) {
        this.tables = tables == null ? Collections.emptyMap() : new LinkedHashMap<>(tables);
        this.capturedAt = LocalDateTime.now();
    }

    public TableSnapshot getTable(String tableName) {
        return tables.get(tableName);
    }

    public Map<String, TableSnapshot> getTables() {
        return Collections.unmodifiableMap(tables);
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }
}
