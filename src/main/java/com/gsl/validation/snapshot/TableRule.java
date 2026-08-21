package com.gsl.validation.snapshot;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One table's participation + comparison rules in a {@link VoucherPersistenceMap} — doc §9/§19.
 *
 * <p>{@link #getFkColumn()}/{@link #getFkValueResolver()} tell {@link SnapshotService} how to
 * fetch this table's rows for one document ({@code SELECT * FROM <table> WHERE <fkColumn> = ?}) —
 * some GV tables key off {@code vchcode} directly ({@code finvchmain}, {@code finvchdet}),
 * others off a varchar {@code entcode} column carrying the same value as text
 * ({@code finpost}, {@code fincosttag} — see {@code FinPostDao}/{@code FinCostTagDao}'s own
 * {@code String entcode} bind parameters). {@code aud2_*} audit-shadow tables are fetched via
 * {@code Aud2SnapshotDao} instead — see {@link SnapshotService}.
 */
@Getter
@Builder(toBuilder = true)
public class TableRule {

    /** Real table name, e.g. {@code "finvchmain"}, {@code "aud2_finvchmain"}. */
    private final String tableName;

    /** Column(s) that uniquely identify a row within this table for this document — used both
     *  for before/after row matching and for the report's "Row (Business Key)" display. */
    @Singular
    private final List<String> businessKeyColumns;

    /** Column(s) that identify a row's business IDENTITY across a delete+reinsert-under-new-
     *  surrogate-key cycle — a coarser key than {@link #businessKeyColumns} that excludes the
     *  surrogate column itself (e.g. {@code finvchmain_code} for {@code fincosttag}/{@code
     *  finvchdn}, not their own {@code code} PK). Used only by
     *  {@code AuditChangeReconstructor}'s DELETE+INSERT reconciliation (confirmed live in
     *  {@code aud2_finvchdn}: same {@code finvchmain_code}, a DELETE immediately followed by an
     *  INSERT under a new {@code code}). Empty by default — {@link #getIdentityColumnsOrDefault()}
     *  falls back to {@link #businessKeyColumns} for every table that doesn't recreate rows. */
    @Singular
    private final List<String> identityColumns;

    /** WHERE-clause column used to scope the capture query to one document. Ignored for
     *  {@code aud2_*} tables (handled by {@code Aud2SnapshotDao}). */
    private final String fkColumn;

    /** Resolves the bind value for {@link #fkColumn} from the {@link DocumentContext} — e.g.
     *  {@code ctx -> ctx.getVchcode()} (bigint column) or {@code ctx -> String.valueOf(ctx.getVchcode())}
     *  (varchar {@code entcode} column). */
    private final Function<DocumentContext, Object> fkValueResolver;

    /** Whether this table participates for a given document at all (e.g. {@code fincosttag}
     *  only when at least one Type2/Type3 line exists; {@code aud2_<table>} only when that base
     *  table is currently audit-active). */
    private final Predicate<DocumentContext> participationCondition;

    /** How BEFORE/AFTER rows within this table are paired up — see {@link RowMatchStrategy}. */
    @Builder.Default
    private final RowMatchStrategy rowMatchStrategy = RowMatchStrategy.BY_KEY;

    /** Expectation applied to every column with no explicit {@link #columnRules} override. */
    @Builder.Default
    private final ChangeExpectation defaultRule = ChangeExpectation.MUST_NOT_CHANGE;

    /** Scenario-agnostic column overrides baked into this table's own shape (e.g.
     *  {@code aud2_*} tables are always row-level {@code INSERT}, never column-diffed).
     *  Scenario-specific overrides are layered on top by {@link ExpectedChangeSet}, not here. */
    @Singular
    private final List<ColumnRule> columnRules;

    /** {@code true} for {@code aud2_*} audit-shadow tables — row-count/INSERT check only, never
     *  column-diffed; drives {@link SnapshotService}'s {@code Aud2SnapshotDao} routing and
     *  {@link SnapshotValidationReporter}'s "Audit Trail" table. */
    public boolean isAuditTable() {
        return tableName != null && tableName.startsWith("aud2_");
    }

    /** {@code true} when this table participates for {@code context} (default: always participates). */
    public boolean participates(DocumentContext context) {
        return participationCondition == null || participationCondition.test(context);
    }

    /** {@link #identityColumns} if declared, else {@link #businessKeyColumns} — see that field's
     *  Javadoc. Never returns {@code null}/empty when {@code businessKeyColumns} isn't. */
    public List<String> getIdentityColumnsOrDefault() {
        return (identityColumns == null || identityColumns.isEmpty()) ? businessKeyColumns : identityColumns;
    }

    /** Looks up an explicit column override, or {@code null} if this column uses the table default. */
    public ColumnRule findColumnRule(String column) {
        if (columnRules == null) return null;
        return columnRules.stream()
                .filter(r -> r.getColumn().equalsIgnoreCase(column))
                .findFirst()
                .orElse(null);
    }
}
