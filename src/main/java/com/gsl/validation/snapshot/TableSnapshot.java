package com.gsl.validation.snapshot;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One table's captured rows at a point in time — doc §15, plan §1. Rows are raw
 * {@code Map<String,Object>} (JDBI's {@code mapToMap()} shape — column name -&gt; value), no
 * per-table model classes needed, per the plan's explicit design choice.
 */
@Getter
public class TableSnapshot {

    private final String tableName;
    private final List<Map<String, Object>> rows;

    public TableSnapshot(String tableName, List<Map<String, Object>> rows) {
        this.tableName = tableName;
        this.rows = rows == null ? Collections.emptyList() : List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }
}
