package com.gsl.utils.dataprovider.fixture.finance;

import lombok.Builder;
import lombok.Value;

/**
 * One planned test combination in the voucher coverage catalog ({@link CoverageCatalog}) —
 * document-only metadata; nothing here drives execution.
 */
@Value
@Builder(toBuilder = true)
public class CoverageRow {

    /** Group label for rows the data sources emit regardless of any {@code *.group} filter. */
    public static final String GROUP_ALWAYS = "ALWAYS";

    /** {@code GV}, {@code AR} or {@code AP}. */
    String module;

    /** {@code Create}, {@code Edit}, {@code Revert} or {@code Delete}. */
    String flow;

    /** Stable scenario id — what executed tests report as their variant id; the primary join key. */
    String variantId;

    /** Fallback join key for rows whose executed test carries no variant id (e.g. AP Revert/Delete,
     *  AP Edit header rows): the full per-row test-case id such as {@code FIN-AP-REVERT-01}.
     *  {@code null} when {@link #variantId} is enough. */
    String testCaseId;

    /** Execution group ({@code MANDATORY}, {@code REGRESSION_PHASE1..3}) or {@link #GROUP_ALWAYS}. */
    String group;

    /** Human-readable description of the scenario. */
    String description;

    /** Scenario-type label (e.g. the {@code ArVoucherScenarioType} constant, or the GV edit scenario type). */
    String scenarioType;

    /** Compact "key fields" summary of the combination's dimensions, for the GV coverage matrix. */
    String keyFields;

    /** {@code true} for a scenario-level placeholder standing in for a DB-expanded family of rows
     *  that cannot be enumerated statically; it joins to executed results by scenario type. */
    boolean dbExpanded;

    /** {@code true} when the group is MANDATORY or {@link #GROUP_ALWAYS} — the "must run" tier. */
    public boolean isMandatoryTier() {
        return "MANDATORY".equals(group) || GROUP_ALWAYS.equals(group);
    }
}
