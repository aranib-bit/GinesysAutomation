package com.gsl.validation.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The full set of {@link TableRule}s for one voucher type — doc §9-10, plan §1.
 *
 * <p>Built once per voucher domain (e.g. {@code VoucherPersistenceMapFactory.build(VoucherDomain.GV)}) and shared
 * by every {@link DocumentContext} of that type; {@link #activeTables(DocumentContext)} narrows
 * it down to the tables that actually participate for one specific document.
 */
public class VoucherPersistenceMap {

    private final List<TableRule> tableRules;

    public VoucherPersistenceMap(List<TableRule> tableRules) {
        this.tableRules = tableRules == null ? Collections.emptyList() : List.copyOf(tableRules);
    }

    public List<TableRule> getTableRules() {
        return tableRules;
    }

    /** Table rules that participate for {@code context}, in declared order. */
    public List<TableRule> activeTables(DocumentContext context) {
        return tableRules.stream()
                .filter(rule -> rule.participates(context))
                .collect(Collectors.toList());
    }

    public TableRule findByTableName(String tableName) {
        return tableRules.stream()
                .filter(r -> r.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElse(null);
    }
}
