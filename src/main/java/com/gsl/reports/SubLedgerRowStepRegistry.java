package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-thread registry that maps a sub-ledger row key (its {@code lineNo}) to the
 * Extent group node created for that row during Pass 1 of the sub-ledger fill.
 *
 * <p>Sub-ledger rows are filled in deliberate passes (Pass 1 name/amount, Pass 2
 * reference site, then adjustments, payment, and — for AP — TDS), and the TDS pass
 * even runs from a different page component and a different predicate call. To report
 * every one of those operations <em>under the same row</em>, Pass 1 captures the row's
 * group node here and the later passes look it up by {@code lineNo} and attach their
 * sub-steps via {@link StepExecutor#stepUnder(ExtentTest, String, StepExecutor.StepAction)}.
 *
 * <p>State is held in a {@link ThreadLocal} — the same approach as {@link ExtentManager} —
 * so it is safe under {@code parallel="methods"}: each test thread sees only its own rows.
 * Always {@link #clear()} at the start of a sub-ledger fill so a thread does not inherit
 * stale nodes from a previously executed test.
 */
public final class SubLedgerRowStepRegistry {

    /** Node + undecorated title + running total of this row's own child-step durations. */
    private static final class RowEntry {
        final ExtentTest node;
        final String baseLabel;
        long totalMs;
        RowEntry(ExtentTest node, String baseLabel) {
            this.node = node;
            this.baseLabel = baseLabel;
        }
    }

    private static final ThreadLocal<Map<String, RowEntry>> ROWS =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private SubLedgerRowStepRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Resets the registry for the current thread. Call at the start of each sub-ledger fill. */
    public static void clear() {
        ROWS.get().clear();
    }

    /**
     * Builds a stable row key. Uses {@code lineNo} when present (the same key payment and
     * TDS already match on); falls back to the row index when {@code lineNo} is blank so
     * rows without a line number still get a unique, lookup-able key.
     */
    public static String key(String lineNo, int rowIndex) {
        return (lineNo != null && !lineNo.isBlank()) ? lineNo.trim() : "__row_" + rowIndex;
    }

    /**
     * Registers the group node for a row key, along with its undecorated title
     * ({@code baseLabel}, the exact string passed to {@link StepExecutor#group}) so
     * {@link #stepUnderRow} can rewrite the title with a running duration total without
     * ever nesting duration text inside itself.
     */
    public static void put(String rowKey, ExtentTest node, String baseLabel) {
        if (rowKey == null) return;
        ROWS.get().put(rowKey, new RowEntry(node, baseLabel));
    }

    /** Looks up a row's entry by exact key, then by trimmed key as a fallback; {@code null} if neither matches. */
    private static RowEntry lookup(String rowKey) {
        if (rowKey == null) return null;
        Map<String, RowEntry> rows = ROWS.get();
        RowEntry entry = rows.get(rowKey);
        if (entry == null && rowKey.trim().length() > 0) {
            entry = rows.get(rowKey.trim());
        }
        return entry;
    }

    /**
     * Runs {@code action} as a child of the row's group node (same pass/fail/screenshot
     * semantics as {@link StepExecutor#stepUnder}), then adds the child's measured duration
     * to the row's running total and rewrites the row's own title to
     * {@code baseLabel + " (" + total + ")"} — so "SL Row N — ..." shows the row's own
     * cumulative elapsed time the same way each child step shows its own, kept in sync as
     * more children attach across passes (Pass 1, Pass 2, adjustments, payment, TDS).
     *
     * <p>If the row key is unknown (defensive), delegates straight to
     * {@link StepExecutor#stepUnder} with a {@code null} parent, which falls back to a plain
     * step under the current node. If {@code action} throws, the exception propagates before
     * any total/title update — the row keeps its last successfully-accumulated duration,
     * mirroring how a leaf step only gets a duration appended on its own pass path.
     */
    public static long stepUnderRow(String rowKey, String stepName, StepExecutor.StepAction action) {
        RowEntry entry = lookup(rowKey);
        ExtentTest node = entry != null ? entry.node : null;
        long durationMs = StepExecutor.stepUnder(node, stepName, action);
        if (entry != null) {
            entry.totalMs += durationMs;
            entry.node.getModel().setName(
                    entry.baseLabel + " (" + StepExecutor.formatDuration(entry.totalMs) + ")");
        }
        return durationMs;
    }
}
