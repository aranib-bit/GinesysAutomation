package com.gsl.validation.db.finance;

import lombok.Builder;
import lombok.Data;

/**
 * Pre-resolved facts one {@code accountClassScenario} invocation needs for its
 * "Database Validation" step — assembled by {@link AccountClassDbContextBuilder} from the Excel
 * row plus whatever {@code uniqueify(...)} actually produced this run (the resolved names are
 * NOT recomputed here; the caller passes in exactly what it submitted to the UI).
 */
@Data
@Builder
public class AccountClassDbContext {

    /** The exact Class Name string submitted to the Add popup this run. */
    private String resolvedClassName;

    /** Class Type label selected in the dropdown (e.g. "Customer", "TDS Agency"). */
    private String expectedClassType;

    /** "CREATE" or "REJECT", from the row's ExpectedOutcome column. */
    private String expectedOutcome;

    // ── Edit (optional) ────────────────────────────────────────────────────────
    private boolean edited;
    /** Null when only the type was changed (name kept). */
    private String newResolvedClassName;
    /** Null when only the name was changed (type kept — expectedClassType still applies). */
    private String newExpectedClassType;

    // ── Duplicate-variant self-check (optional) ───────────────────────────────
    /** "EXACT", "CASE", or null when this row has no duplicate-variant check. */
    private String duplicateVariant;
    /** The exact/case-varied name that was attempted and must have been rejected. */
    private String duplicateVariantName;
}
