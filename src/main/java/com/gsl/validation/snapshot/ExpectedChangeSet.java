package com.gsl.validation.snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Named bundle of scenario-specific {@link ColumnRule} overrides, layered on top of a
 * {@link VoucherPersistenceMap}'s table-level defaults — doc §25, plan §1.
 *
 * <p>Precedence (highest first), applied by {@link DiffEngine}: this change set's
 * <b>row-scoped</b> override (a rule that applies only to rows whose business-key display string
 * contains {@link ScopedRule#rowKeyContains()}, e.g. only the one edited {@code finvchdet} row
 * identified by {@code "glcode=4021"}) &gt; this change set's <b>table-wide</b> override (applies
 * to every row of that table — e.g. header edits, which only ever have one {@code finvchmain}
 * row anyway) &gt; the {@link TableRule}'s own baked-in column override (e.g. {@code aud2_*}'s
 * always-INSERT rule) &gt; the table's {@link TableRule#getDefaultRule()}.
 *
 * <p>Row scoping is necessary because a table like {@code finvchdet} may have several rows in one
 * voucher (one per Expense/Income Detail line) — a detail-line edit's {@code damount}/{@code
 * isreversal} MUST_CHANGE override must apply only to the one edited line, leaving every other
 * line's {@code damount} on the table's normal MUST_NOT_CHANGE default.
 */
public class ExpectedChangeSet {

    /** One override, optionally scoped to rows whose business-key display string contains
     *  {@link #rowKeyContains()} ({@code null} = applies table-wide, to every row). */
    public record ScopedRule(String rowKeyContains, ColumnRule rule) {}

    /** A declared expectation that a whole row (not a column) is allowed to appear/disappear
     *  between BEFORE and AFTER for rows matching {@code matcher} — see
     *  {@link DiffEngine#diffDataTable}. {@code expectation} must be {@link ChangeExpectation#INSERT}
     *  or {@link ChangeExpectation#DELETE}. */
    public record RowExpectation(ChangeExpectation expectation, Predicate<Map<String, Object>> matcher) {}

    private final String name;
    private final Map<String, List<ScopedRule>> overridesByTable;
    private final Map<String, List<RowExpectation>> rowExpectationsByTable;

    private ExpectedChangeSet(String name, Map<String, List<ScopedRule>> overridesByTable,
            Map<String, List<RowExpectation>> rowExpectationsByTable) {
        this.name = name;
        this.overridesByTable = overridesByTable;
        this.rowExpectationsByTable = rowExpectationsByTable;
    }

    public String getName() {
        return name;
    }

    /**
     * Looks up this change set's override for one (table, business key, column) — a row-scoped
     * match wins over a table-wide one; {@code null} if neither applies (falls through to the
     * {@link TableRule}'s own rules).
     */
    public ColumnRule findOverride(String tableName, String businessKey, String column) {
        List<ScopedRule> rules = overridesByTable.get(tableName.toLowerCase());
        if (rules == null) return null;

        Set<String> keySegments = businessKeySegments(businessKey);

        ColumnRule rowScoped = rules.stream()
                .filter(r -> r.rowKeyContains() != null
                        && matchesAllSegments(keySegments, r.rowKeyContains())
                        && r.rule().getColumn().equalsIgnoreCase(column))
                .map(ScopedRule::rule)
                .findFirst().orElse(null);
        if (rowScoped != null) return rowScoped;

        return rules.stream()
                .filter(r -> r.rowKeyContains() == null && r.rule().getColumn().equalsIgnoreCase(column))
                .map(ScopedRule::rule)
                .findFirst().orElse(null);
    }

    /**
     * Splits a {@code "col1=val1, col2=val2"} business-key display string (see
     * {@code DiffEngine#businessKeyOf}) into its individual {@code "col=val"} segments.
     */
    private static Set<String> businessKeySegments(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) return Set.of();
        return Arrays.stream(businessKey.split(",\\s*")).collect(Collectors.toSet());
    }

    /**
     * {@code true} when every comma-separated segment of {@code rowKeyContains} (e.g.
     * {@code "glcode=4"}, or a compound {@code "glcode=4, slcode=33"}) is present verbatim in
     * {@code keySegments} — exact-segment matching, not raw substring containment. Raw
     * {@code businessKey.contains(rowKeyContains)} would incorrectly match {@code "glcode=1"}
     * against a business key containing {@code "glcode=15"}/{@code "glcode=10"} in the same
     * document (a numeric-prefix collision); splitting both sides into whole segments and
     * requiring exact membership avoids that entirely.
     */
    private static boolean matchesAllSegments(Set<String> keySegments, String rowKeyContains) {
        return Arrays.stream(rowKeyContains.split(",\\s*")).allMatch(keySegments::contains);
    }

    /** {@code true} if this change set declares {@code expectation} (INSERT/DELETE) for
     *  {@code tableName}, matched against whichever row is present ({@code row} = the AFTER row for
     *  an INSERT check, the BEFORE row for a DELETE check). */
    public boolean isRowChangeExpected(String tableName, ChangeExpectation expectation, Map<String, Object> row) {
        List<RowExpectation> expectations = rowExpectationsByTable.get(tableName.toLowerCase());
        if (expectations == null || row == null) return false;
        return expectations.stream().anyMatch(e -> e.expectation() == expectation && e.matcher().test(row));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final Map<String, List<ScopedRule>> overrides = new LinkedHashMap<>();
        private final Map<String, List<RowExpectation>> rowExpectations = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        /** Adds a table-wide override — applies to every row of {@code tableName}. */
        public Builder rule(String tableName, ColumnRule rule) {
            overrides.computeIfAbsent(tableName.toLowerCase(), k -> new ArrayList<>())
                    .add(new ScopedRule(null, rule));
            return this;
        }

        /** Adds a row-scoped override — applies only to rows whose business-key display string
         *  contains {@code rowKeyContains} (e.g. {@code "glcode=4021"}). */
        public Builder rule(String tableName, String rowKeyContains, ColumnRule rule) {
            overrides.computeIfAbsent(tableName.toLowerCase(), k -> new ArrayList<>())
                    .add(new ScopedRule(rowKeyContains, rule));
            return this;
        }

        public Builder rules(String tableName, List<ColumnRule> rules) {
            for (ColumnRule r : rules) rule(tableName, r);
            return this;
        }

        /** Declares that a row matching {@code matcher} is expected to newly appear in
         *  {@code tableName} between BEFORE and AFTER (e.g. a {@code fincosttag} row recreated
         *  under a new surrogate key) — see {@link DiffEngine#diffDataTable}. */
        public Builder expectRowInsert(String tableName, Predicate<Map<String, Object>> matcher) {
            rowExpectations.computeIfAbsent(tableName.toLowerCase(), k -> new ArrayList<>())
                    .add(new RowExpectation(ChangeExpectation.INSERT, matcher));
            return this;
        }

        /** Declares that a row matching {@code matcher} is expected to disappear from
         *  {@code tableName} between BEFORE and AFTER — see {@link DiffEngine#diffDataTable}. */
        public Builder expectRowDelete(String tableName, Predicate<Map<String, Object>> matcher) {
            rowExpectations.computeIfAbsent(tableName.toLowerCase(), k -> new ArrayList<>())
                    .add(new RowExpectation(ChangeExpectation.DELETE, matcher));
            return this;
        }

        /** Merges another change set's overrides into this builder — used by
         *  {@code GeneralVoucherEditChangeSets.detailLineEdit} to layer its one extra
         *  {@code finvchdet} row override on top of {@code headerEdit}'s rules. */
        public Builder mergeFrom(ExpectedChangeSet other) {
            other.overridesByTable.forEach((table, rules) ->
                    overrides.computeIfAbsent(table, k -> new ArrayList<>()).addAll(rules));
            other.rowExpectationsByTable.forEach((table, expectations) ->
                    rowExpectations.computeIfAbsent(table, k -> new ArrayList<>()).addAll(expectations));
            return this;
        }

        public ExpectedChangeSet build() {
            return new ExpectedChangeSet(name, overrides, rowExpectations);
        }
    }
}
