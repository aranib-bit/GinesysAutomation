package com.gsl.validation.snapshot.report;

/**
 * Presentation-layer classification of one column check — doc
 * ({@code Database_Snapshot_Validation_Representation.md}) §10's
 * {@code USER_CHANGE/SYSTEM_CHANGE/CASCADE_CHANGE/AUDIT_CHANGE/IGNORED/UNEXPECTED_CHANGE}
 * vocabulary, derived from the engine's existing {@code ChangeExpectation} + pass/fail — never a
 * second, hand-authored classification. Lives in this separate {@code .report} package (not
 * {@code com.gsl.validation.snapshot} itself) so the comparison engine ({@code DiffEngine}/
 * {@code AuditComparator}) never depends on a presentation-only concept — doc §18's "separate
 * comparison engine from presentation layer" principle.
 */
public enum ChangeClassification {
    /** {@code ChangeExpectation.MUST_CHANGE} that passed — a business/user-driven change. */
    USER_CHANGE("Fields you filled in via the UI"),
    /** {@code ChangeExpectation.SYSTEM_CHANGE} that passed — a framework-maintained column
     *  (last-modified timestamp/user) that changed as a side effect of the edit. */
    SYSTEM_CHANGE("Fields the system fills in automatically (timestamps, mandatory codes, IDs)"),
    /** A row-level INSERT/DELETE on a table known to delete+reinsert under a new surrogate key
     *  (e.g. {@code fincosttag}, {@code finvchdn}) for what is, from the business perspective, one
     *  update — not a genuinely new/removed business record. */
    CASCADE_CHANGE("New rows created as a side effect of this action (not directly asserted here)"),
    /** A column present in {@code aud2_active_tab_col.exclude_update_of_column} for this table —
     *  the exact "housekeeping" columns the audit trigger itself ignores (e.g.
     *  {@code last_access_time}, {@code dataversion}). */
    AUDIT_CHANGE("Fields excluded from audit-trail comparison for this table"),
    /** {@code ChangeExpectation.IGNORE} — deliberately excluded from comparison. */
    IGNORED("Fields deliberately excluded from comparison"),
    /** {@code ChangeExpectation.MUST_NOT_CHANGE} that failed — a genuine unexpected change. */
    UNEXPECTED_CHANGE("Fields that changed (or didn't) when they shouldn't have — a real failure"),
    /** A {@code MUST_NOT_CHANGE} mismatch on a column marked {@code ColumnRule.knownIssue} — a
     *  confirmed, already-tracked defect. Does not fail the test (see {@code ColumnCheckResult
     *  #knownIssueNote}), but is classified separately from {@link #UNEXPECTED_CHANGE} so it isn't
     *  miscounted as either a genuine failure or an ordinary passing change. */
    KNOWN_ISSUE("A confirmed, already-tracked defect — visible but doesn't fail the test");

    private final String description;

    ChangeClassification(String description) {
        this.description = description;
    }

    /** Plain-English explanation for report display (Item S) — a reader shouldn't need to know
     *  what "CASCADE_CHANGE" means to understand the Change Classification Summary table. */
    public String description() {
        return description;
    }
}
