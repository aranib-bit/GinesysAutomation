package com.gsl.reports;

import com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters;
import com.gsl.utils.dataprovider.fixture.finance.CoverageRow;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure numbers behind the coverage workbook's Dashboard, coverage sheets and Group Matrix -
 * everything is an {@code int}/{@code double}, never a pre-formatted string, so the sheets can
 * write real numeric cells. No POI, Selenium or Extent access: unit tested directly.
 *
 * <p><b>Row states</b> ({@link RowState}): a catalog row that has a result is {@code PASS}/
 * {@code FAIL}/{@code SKIP}; one that has none is split three ways -
 * {@code NOT_SELECTED} (the active group/tag filter excludes it, so it was never meant to run),
 * {@code SELECTED_NOT_RUN} (selected and its module/flow did run, yet this row produced nothing:
 * data-provider shortage, skip before binding, ...) and {@code NOT_RUN} (selected, but nothing of
 * its module/flow ran at all - the flow was not part of this suite).
 */
public final class CoverageStats {

    public enum RowState {
        PASS("PASS"), FAIL("FAIL"), SKIP("SKIP"),
        NOT_SELECTED("Not selected (group filter)"),
        SELECTED_NOT_RUN("Selected but not run"),
        NOT_RUN("Not run"),
        DB_EXPANDED("DB-EXPANDED");

        private final String label;

        RowState(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Counters for one bucket (module/flow, group, ...). */
    public static final class Counts {
        public int planned, notSelected, selectedNotRun, notRun, executed, pass, fail, skip, flaky;
        /** Executed results that matched a catalog row (the numerator of coverage). */
        public int executedCatalogued;
        public long durationMs;

        /** Planned minus not-selected: what this run was meant to cover. */
        public int selected() {
            return planned - notSelected;
        }

        /** pass / executed, {@code 0} when nothing executed. */
        public double passRate() {
            return executed == 0 ? 0 : pass / (double) executed;
        }

        /** catalogued executed / selected, {@code 0} when nothing was selected. */
        public double coverage() {
            return selected() <= 0 ? 0 : Math.min(1.0, executedCatalogued / (double) selected());
        }

    }

    /** Display order of module/flow buckets and groups. */
    public static final List<String> MODULES = List.of("GV", "AR", "AP");
    private static final List<String> FLOWS = List.of("Create", "Edit", "Revert", "Delete", "UI Validation");
    public static final List<String> GROUPS = List.of("MANDATORY", "REGRESSION_PHASE1", "REGRESSION_PHASE2",
            "REGRESSION_PHASE3", CoverageRow.GROUP_ALWAYS, "VARIES");

    /** Bucket key {@code "GV|Create"} to counters, in module then flow order. */
    public final Map<String, Counts> byModuleFlow = new LinkedHashMap<>();
    /** Group name to counters (all modules). */
    public final Map<String, Counts> byGroup = new LinkedHashMap<>();
    /** Module then group to counters ({@code "GV|MANDATORY"}). */
    public final Map<String, Counts> byModuleGroup = new LinkedHashMap<>();
    public final Counts total = new Counts();
    /** Failure/skip category to number of FAIL+SKIP results. */
    public final Map<FailureCategory, Integer> categories = new EnumMap<>(FailureCategory.class);
    /** State of each catalog row, parallel to {@code join.catalogRows}. */
    public final List<RowState> rowStates = new ArrayList<>();
    /** Executed results that no catalog row claimed. */
    public int uncatalogued;

    /** Flows (module|flow) that produced at least one result. */
    private final Set<String> ranFlows = new HashSet<>();

    public static String key(String module, String flow) {
        return module + "|" + flow;
    }

    /** Classifies one catalog row - see the class Javadoc. */
    public static RowState classify(CoverageWorkbookBuilder.Matched m, ActiveGroupFilters filters, Set<String> ranFlows) {
        CoverageRow row = m.row;
        if (row.isDbExpanded()) return RowState.DB_EXPANDED;
        if (m.result != null) {
            return switch (m.result.getStatus()) {
                case "PASS" -> RowState.PASS;
                case "FAIL" -> RowState.FAIL;
                default -> RowState.SKIP;
            };
        }
        if (filters != null && !filters.isSelected(row)) return RowState.NOT_SELECTED;
        return ranFlows.contains(key(row.getModule(), row.getFlow())) ? RowState.SELECTED_NOT_RUN : RowState.NOT_RUN;
    }

    public static CoverageStats compute(CoverageWorkbookBuilder.JoinResult join, List<CoverageResult> results,
                                        ActiveGroupFilters filters) {
        CoverageStats s = new CoverageStats();
        for (CoverageResult r : results) s.ranFlows.add(key(r.getModule(), r.getFlow()));

        // Stable bucket order: known modules x flows first.
        for (String m : MODULES) {
            for (String f : FLOWS) s.byModuleFlow.put(key(m, f), new Counts());
        }
        for (String g : GROUPS) s.byGroup.put(g, new Counts());

        for (CoverageWorkbookBuilder.Matched m : join.catalogRows) {
            RowState st = classify(m, filters, s.ranFlows);
            s.rowStates.add(st);
            if (st == RowState.DB_EXPANDED) continue;
            Counts mf = s.byModuleFlow.computeIfAbsent(key(m.row.getModule(), m.row.getFlow()), k -> new Counts());
            Counts g = s.byGroup.computeIfAbsent(groupOf(m.row), k -> new Counts());
            Counts mg = s.byModuleGroup.computeIfAbsent(key(m.row.getModule(), groupOf(m.row)), k -> new Counts());
            for (Counts c : List.of(mf, g, mg, s.total)) {
                c.planned++;
                switch (st) {
                    case NOT_SELECTED -> c.notSelected++;
                    case SELECTED_NOT_RUN -> c.selectedNotRun++;
                    case NOT_RUN -> c.notRun++;
                    default -> { }
                }
            }
        }
        for (CoverageResult r : results) {
            CoverageRow row = join.rowByResult.get(r);
            List<Counts> targets = new ArrayList<>();
            targets.add(s.byModuleFlow.computeIfAbsent(key(r.getModule(), r.getFlow()), k -> new Counts()));
            targets.add(s.total);
            if (row != null) {
                targets.add(s.byGroup.computeIfAbsent(groupOf(row), k -> new Counts()));
                targets.add(s.byModuleGroup.computeIfAbsent(key(r.getModule(), groupOf(row)), k -> new Counts()));
            } else {
                s.uncatalogued++;
            }
            for (Counts c : targets) {
                c.executed++;
                c.durationMs += r.getDurationMs();
                if (row != null) c.executedCatalogued++;
                switch (r.getStatus()) {
                    case "PASS" -> c.pass++;
                    case "FAIL" -> c.fail++;
                    default -> c.skip++;
                }
                if (r.isPassedOnRetry()) c.flaky++;
            }
            if (!"PASS".equals(r.getStatus())) {
                s.categories.merge(r.getFailureCategory() == null ? FailureCategory.OTHER : r.getFailureCategory(), 1, Integer::sum);
            }
        }
        // Drop empty module/flow buckets (e.g. AR Delete when no such tests exist anywhere).
        s.byModuleFlow.values().removeIf(c -> c.planned == 0 && c.executed == 0);
        s.byGroup.values().removeIf(c -> c.planned == 0 && c.executed == 0);
        return s;
    }

    /** The row's group, or {@code ALWAYS} when blank. */
    public static String groupOf(CoverageRow row) {
        return row.getGroup() == null || row.getGroup().isBlank() ? CoverageRow.GROUP_ALWAYS : row.getGroup();
    }

    /** Categories sorted by count descending (ties by label), without {@link FailureCategory#NONE}. */
    public List<Map.Entry<FailureCategory, Integer>> topCategories() {
        List<Map.Entry<FailureCategory, Integer>> list = new ArrayList<>(categories.entrySet());
        list.removeIf(e -> e.getKey() == FailureCategory.NONE);
        list.sort((a, b) -> !a.getValue().equals(b.getValue()) ? Integer.compare(b.getValue(), a.getValue())
                : a.getKey().label().compareTo(b.getKey().label()));
        return list;
    }

}
