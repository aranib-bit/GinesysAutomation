package com.gsl.validation.snapshot.audit;

import com.gsl.annotations.AuditTrace;
import com.gsl.validation.snapshot.ColumnCheckResult;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.TableSnapshot;
import com.gsl.validation.snapshot.ValidationFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <b>AuditComparator</b> — compares the Expected Audit (derived directly from the data table's
 * own {@link ColumnCheckResult}s, not hand-authored twice) against the Actual Audit
 * ({@link AuditChangeReconstructor}'s output), field by field, reusing
 * {@link DbValueNormalizer}. Also drives {@code DiffEngine}'s existence-check fix via
 * {@link AuditComparisonResult#expectedAnyChange()} and runs the old-value continuity cross-check
 * (doc "Decisions confirmed with the user" — the audit trail's own previous entry vs. the live
 * BEFORE snapshot, surfaced as a distinct {@code "[AUDIT CHAIN DRIFT]"} finding, never silently
 * discarded even when the primary comparison otherwise passes).
 */
public class AuditComparator {

    private final AuditChangeReconstructor reconstructor = new AuditChangeReconstructor();

    /**
     * @param dataTableRule        the base (non-audit) table's {@link TableRule}
     * @param dataTableColumnChecks that table's already-computed {@link ColumnCheckResult}s for
     *                             this document (from {@code DiffEngine.diffDataTable})
     * @param beforeAuditRows      {@code aud2_<table>} rows captured before the operation
     * @param afterAuditRows       {@code aud2_<table>} rows captured after the operation
     * @param beforeDataRows       the base table's own BEFORE snapshot
     * @param excludedColumnsUpper {@code aud2_active_tab_col.exclude_update_of_column} for this table
     */
    @AuditTrace("compare")
    public AuditComparisonResult compare(TableRule dataTableRule, List<ColumnCheckResult> dataTableColumnChecks,
            TableSnapshot beforeAuditRows, TableSnapshot afterAuditRows, TableSnapshot beforeDataRows,
            Set<String> excludedColumnsUpper) {

        String tableName = dataTableRule.getTableName();
        List<ExpectedAuditChange> expected = deriveExpected(tableName, dataTableColumnChecks, excludedColumnsUpper);
        List<AuditChange> actual = reconstructor.reconstruct(dataTableRule, beforeAuditRows, afterAuditRows,
                beforeDataRows, excludedColumnsUpper);

        List<AuditFieldCheckResult> fieldChecks = new ArrayList<>();
        List<ValidationFailure> failures = new ArrayList<>();

        for (ExpectedAuditChange exp : expected) {
            AuditChange match = findMatch(actual, dataTableRule, exp);
            if (match == null) {
                String message = "[" + tableName + "] " + exp.recordId + (exp.column != null ? " ." + exp.column : "")
                        + " — expected an audit-recorded " + exp.operation + " but no matching audit change was found";
                ValidationFailure failure = new ValidationFailure(tableName, exp.recordId, exp.column,
                        exp.operation + (exp.expectedNew != null ? " (=" + exp.expectedNew + ")" : ""),
                        "(no audit entry)", message, ValidationFailure.Severity.ERROR);
                fieldChecks.add(AuditFieldCheckResult.fail(tableName, exp.recordId, exp.operation, exp.column,
                        DbValueNormalizer.displayString(exp.expectedOld), DbValueNormalizer.displayString(exp.expectedNew),
                        "—", "—", failure));
                failures.add(failure);
                continue;
            }
            // exp.expectedOld/expectedNew are already display-formatted Strings (deriveExpected()
            // builds them from ColumnCheckResult.beforeDisplay()/afterDisplay()), while
            // match.oldValue()/newValue() are raw, unnormalized JDBI values (Long/BigDecimal/etc.
            // for numeric columns — see AuditChangeReconstructor). Comparing them directly via
            // DbValueNormalizer.equalsNormalized() compares a String against a Number and is
            // always false regardless of actual value, even though both sides render identically
            // once displayString() is applied — so run the actual side through the same formatter
            // used for the expected side (and for this report's own display) before comparing.
            boolean valuesAgree = exp.column == null // row-level INSERT/DELETE — presence of the match is enough
                    || (java.util.Objects.equals(DbValueNormalizer.displayString(exp.expectedOld), DbValueNormalizer.displayString(match.oldValue()))
                        && java.util.Objects.equals(DbValueNormalizer.displayString(exp.expectedNew), DbValueNormalizer.displayString(match.newValue())));
            if (valuesAgree) {
                fieldChecks.add(AuditFieldCheckResult.pass(tableName, exp.recordId, exp.operation, exp.column,
                        DbValueNormalizer.displayString(exp.expectedOld), DbValueNormalizer.displayString(exp.expectedNew),
                        DbValueNormalizer.displayString(match.oldValue()), DbValueNormalizer.displayString(match.newValue())));
            } else {
                String message = "[" + tableName + "] " + exp.recordId + " ." + exp.column
                        + " — audit trail recorded a different value than the live data table shows: expected '"
                        + DbValueNormalizer.displayString(exp.expectedOld) + "' → '" + DbValueNormalizer.displayString(exp.expectedNew)
                        + "', audit shows '" + DbValueNormalizer.displayString(match.oldValue()) + "' → '"
                        + DbValueNormalizer.displayString(match.newValue()) + "'";
                ValidationFailure failure = new ValidationFailure(tableName, exp.recordId, exp.column,
                        DbValueNormalizer.displayString(exp.expectedOld) + " → " + DbValueNormalizer.displayString(exp.expectedNew),
                        DbValueNormalizer.displayString(match.oldValue()) + " → " + DbValueNormalizer.displayString(match.newValue()),
                        message, ValidationFailure.Severity.ERROR);
                fieldChecks.add(AuditFieldCheckResult.fail(tableName, exp.recordId, exp.operation, exp.column,
                        DbValueNormalizer.displayString(exp.expectedOld), DbValueNormalizer.displayString(exp.expectedNew),
                        DbValueNormalizer.displayString(match.oldValue()), DbValueNormalizer.displayString(match.newValue()),
                        failure));
                failures.add(failure);
            }
        }

        // Actual audit changes with no corresponding expected declaration — surfaced as informational
        // PASS rows (visible in the report, never silently dropped) rather than failed, since a
        // legitimate cascade the ExpectedChangeSet simply hasn't modeled yet shouldn't block the test.
        for (AuditChange act : actual) {
            if (expected.stream().noneMatch(exp -> matches(act, dataTableRule, exp))) {
                fieldChecks.add(AuditFieldCheckResult.pass(tableName, act.recordId(), act.operation(), act.column(),
                        "(not asserted)", "(not asserted)",
                        DbValueNormalizer.displayString(act.oldValue()), DbValueNormalizer.displayString(act.newValue())));
            }
        }

        // Old-value continuity cross-check — never silently discarded, even when everything above passed.
        runContinuityCheck(dataTableRule, beforeAuditRows, beforeDataRows, excludedColumnsUpper, actual, fieldChecks, failures);

        boolean expectedAnyChange = !expected.isEmpty();
        return new AuditComparisonResult(expectedAnyChange, fieldChecks, failures);
    }

    /** One expected audit-side change, derived from the data table's own diff — never hand-authored twice. */
    private record ExpectedAuditChange(String recordId, AuditOperation operation, String column,
            Object expectedOld, Object expectedNew) {}

    @AuditTrace("deriveExpected")
    private List<ExpectedAuditChange> deriveExpected(String tableName, List<ColumnCheckResult> checks,
            Set<String> excludedColumnsUpper) {
        List<ExpectedAuditChange> expected = new ArrayList<>();
        if (checks == null) return expected;
        Set<String> excluded = excludedColumnsUpper == null ? Set.of() : excludedColumnsUpper;

        for (ColumnCheckResult check : checks) {
            if (!check.passed()) continue;

            if ("<row>".equals(check.column())) {
                String label = check.expectationLabel();
                if (label.startsWith("EXPECTED INSERT")) {
                    expected.add(new ExpectedAuditChange(check.businessKey(), AuditOperation.INSERT, null, null, null));
                } else if (label.startsWith("EXPECTED DELETE")) {
                    expected.add(new ExpectedAuditChange(check.businessKey(), AuditOperation.DELETE, null, null, null));
                }
                continue;
            }

            String label = check.expectationLabel();
            boolean isRealChangeExpectation = label.startsWith("MUST_CHANGE") || label.startsWith("SYSTEM_CHANGE");
            boolean actuallyDiffered = !java.util.Objects.equals(check.beforeDisplay(), check.afterDisplay());
            boolean excludedColumn = excluded.contains(check.column().toUpperCase());
            if (isRealChangeExpectation && actuallyDiffered && !excludedColumn) {
                expected.add(new ExpectedAuditChange(check.businessKey(), AuditOperation.UPDATE, check.column(),
                        check.beforeDisplay(), check.afterDisplay()));
            }
        }
        return expected;
    }

    private AuditChange findMatch(List<AuditChange> actual, TableRule dataTableRule, ExpectedAuditChange exp) {
        return actual.stream().filter(a -> matches(a, dataTableRule, exp)).findFirst().orElse(null);
    }

    private boolean matches(AuditChange act, TableRule dataTableRule, ExpectedAuditChange exp) {
        String tableName = dataTableRule.getTableName();
        if (!act.table().equalsIgnoreCase(tableName)) return false;
        if (exp.column == null) {
            // Row-level INSERT/DELETE — match by operation only; recordId may legitimately differ
            // (e.g. a recreated row under a new surrogate key) so isn't compared for row-level matches.
            if (act.operation() == exp.operation && act.column() == null) return true;
            // A table configured with a coarser identity (VoucherPersistenceMapFactory's
            // identityColumn(...), e.g. fincosttag) has its DELETE+INSERT pair reconciled by
            // AuditChangeReconstructor into a single UPDATE of the surrogate business-key column
            // (see AuditChangeReconstructor#diffReconciledUpdate), rather than two standalone
            // row-level audit entries. That one reconciled UPDATE IS the audit trail's own record of
            // both the DELETE and the INSERT halves of the business change, so it satisfies either
            // row-level expectation — confirmed live: a real GV header edit produced exactly one
            // aud2_fincosttag UPDATE-of-code change for a DELETE(code=X)+INSERT(code=Y) pair, and
            // both the expected DELETE and expected INSERT should be satisfied by it, not treated as
            // two unmatched expectations.
            List<String> businessKeyColumns = dataTableRule.getBusinessKeyColumns();
            return act.operation() == AuditOperation.UPDATE
                    && businessKeyColumns != null
                    && businessKeyColumns.stream().anyMatch(col -> col.equalsIgnoreCase(act.column()));
        }
        return act.operation() == AuditOperation.UPDATE
                && act.recordId().equals(exp.recordId)
                && exp.column.equalsIgnoreCase(act.column());
    }

    /**
     * Cross-checks, for each in-place UPDATE column, whether the audit trail's own most-recent
     * PRIOR entry for that business key agrees with what the live table actually held just before
     * this operation. A mismatch means something changed the live row without leaving (or without
     * this framework being able to find) a matching audit trail — a genuine audit-integrity finding,
     * distinct from a normal Expected-vs-Actual field mismatch, surfaced whether or not everything
     * else passed.
     */
    @AuditTrace("runContinuityCheck")
    private void runContinuityCheck(TableRule dataTableRule, TableSnapshot beforeAuditRows,
            TableSnapshot beforeDataRows, Set<String> excludedColumnsUpper, List<AuditChange> actual,
            List<AuditFieldCheckResult> fieldChecks, List<ValidationFailure> failures) {

        if (beforeAuditRows == null || beforeAuditRows.getRows().isEmpty() || beforeDataRows == null) return;
        String tableName = dataTableRule.getTableName();
        Set<String> excluded = excludedColumnsUpper == null ? Set.of() : excludedColumnsUpper;

        for (AuditChange change : actual) {
            if (change.operation() != AuditOperation.UPDATE || change.column() == null) continue;
            if (excluded.contains(change.column().toUpperCase())) continue;

            Map<String, Object> priorAuditRow = mostRecentPriorRow(dataTableRule, beforeAuditRows, change.recordId());
            Map<String, Object> liveBeforeRow = findByBusinessKey(dataTableRule, beforeDataRows, change.recordId());
            if (priorAuditRow == null || liveBeforeRow == null) continue;

            Object priorAuditValue = DbValueNormalizer.getIgnoreCase(priorAuditRow, change.column());
            Object liveBeforeValue = DbValueNormalizer.getIgnoreCase(liveBeforeRow, change.column());

            boolean consistent = DbValueNormalizer.equalsNormalized(priorAuditValue, liveBeforeValue);
            String displayPrior = DbValueNormalizer.displayString(priorAuditValue);
            String displayLive = DbValueNormalizer.displayString(liveBeforeValue);

            if (!consistent) {
                String message = "[AUDIT CHAIN DRIFT] [" + tableName + "] " + change.recordId() + " ." + change.column()
                        + " — the audit trail's most recent PRIOR entry recorded '" + displayPrior
                        + "' but the live table held '" + displayLive + "' just before this operation; "
                        + "something changed this column without a matching audit entry the framework could find.";
                ValidationFailure failure = new ValidationFailure(tableName, change.recordId(), change.column(),
                        displayPrior, displayLive, message, ValidationFailure.Severity.ERROR);
                fieldChecks.add(AuditFieldCheckResult.fail(tableName, change.recordId(), AuditOperation.UPDATE,
                        "[CHAIN] " + change.column(), displayPrior, displayPrior, displayPrior, displayLive, failure));
                failures.add(failure);
            }
        }
    }

    /** Most recent {@code aud2_<table>} row in {@code beforeAuditRows} for {@code businessKey} — by
     *  numeric {@code transaction_id} when parsable, else lexicographic (both observed to be
     *  monotonically increasing per-vchcode in this schema). */
    private Map<String, Object> mostRecentPriorRow(TableRule dataTableRule, TableSnapshot beforeAuditRows, String businessKey) {
        Map<String, Object> best = null;
        for (Map<String, Object> row : beforeAuditRows.getRows()) {
            if (!businessKeyOf(dataTableRule, row).equals(businessKey)) continue;
            if (best == null || isLater(row, best)) best = row;
        }
        return best;
    }

    private boolean isLater(Map<String, Object> candidate, Map<String, Object> current) {
        Object candidateId = DbValueNormalizer.getIgnoreCase(candidate, "transaction_id");
        Object currentId = DbValueNormalizer.getIgnoreCase(current, "transaction_id");
        try {
            return Long.parseLong(String.valueOf(candidateId)) > Long.parseLong(String.valueOf(currentId));
        } catch (NumberFormatException e) {
            return String.valueOf(candidateId).compareTo(String.valueOf(currentId)) > 0;
        }
    }

    private static Map<String, Object> findByBusinessKey(TableRule tableRule, TableSnapshot snapshot, String businessKey) {
        if (snapshot == null) return null;
        for (Map<String, Object> row : snapshot.getRows()) {
            if (businessKeyOf(tableRule, row).equals(businessKey)) return row;
        }
        return null;
    }

    private static String businessKeyOf(TableRule tableRule, Map<String, Object> row) {
        List<String> columns = tableRule.getBusinessKeyColumns();
        if (columns == null || columns.isEmpty()) return "(no key)";
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            if (sb.length() > 0) sb.append(", ");
            Object raw = DbValueNormalizer.getIgnoreCase(row, col);
            sb.append(col).append('=').append(DbValueNormalizer.displayString(raw));
        }
        return sb.toString();
    }
}
