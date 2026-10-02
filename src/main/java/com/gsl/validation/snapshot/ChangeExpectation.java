package com.gsl.validation.snapshot;

/**
 * What is expected to happen to one column (or, for {@link #INSERT}/{@link #DELETE}/
 * {@link #RECREATED}, one whole row) between a BEFORE and AFTER {@link VoucherSnapshot} —
 * the generic snapshot/diff engine's classification vocabulary (see the approved plan's
 * §1, doc §12).
 *
 * <ul>
 *   <li>{@link #MUST_CHANGE} — the column is expected to differ (e.g. {@code narration},
 *       {@code refno}, {@code vchdt} on a header edit). Paired with
 *       {@link ComparisonStrategy#EXPECTED_VALUE} when the exact new value is known, or
 *       {@link ComparisonStrategy#NOT_EQUAL} when only "it changed" is asserted.</li>
 *   <li>{@link #SYSTEM_CHANGE} — a framework-maintained column expected to change as a
 *       side effect of the edit, not because the test explicitly set it (e.g.
 *       {@code lastmodifiedon}/{@code lastmodifiedby}). Paired with
 *       {@link ComparisonStrategy#CURRENT_TIMESTAMP_TOLERANCE}/{@link ComparisonStrategy#CURRENT_USER}.</li>
 *   <li>{@link #MUST_NOT_CHANGE} — the column must be identical before/after (the
 *       {@link VoucherPersistenceMap}'s default for every column not explicitly overridden).
 *       Paired with {@link ComparisonStrategy#UNCHANGED}.</li>
 *   <li>{@link #INSERT} — row-level: a NEW row is expected to appear (e.g. one
 *       {@code aud2_<table>} audit row per edit, or a genuinely new {@code finvchdet} line).</li>
 *   <li>{@link #DELETE} — row-level: a row present BEFORE is expected to be gone AFTER.</li>
 *   <li>{@link #RECREATED} — row-level: the business-identity row is expected to be
 *       delete+reinserted rather than updated in place (see {@code TableRule.RowMatchStrategy
 *       #BY_BUSINESS_IDENTITY_RECREATED}) — Phase 2 territory once confirmed live, per the
 *       plan's "Open item" on {@code finvchdet}/{@code finpost}.</li>
 *   <li>{@link #IGNORE} — column is deliberately excluded from comparison (e.g. a column
 *       known to be volatile/irrelevant for this scenario).</li>
 *   <li>{@link #MANDATORY} — column-level, applies to a freshly-inserted (or, symmetrically,
 *       freshly-deleted) row: this specific column must never be null/blank regardless of what
 *       else the row's INSERT/DELETE acceptance already confirmed (e.g. {@code finvchmain.ycode}/
 *       {@code vchtype}/{@code entry_source} on Create — a null here is a genuine data-integrity
 *       bug, not a display nuance). Paired with {@link ComparisonStrategy#NOT_NULL}. Distinct from
 *       {@link #MUST_CHANGE}: a mandatory column isn't asserted to differ from BEFORE (there is no
 *       BEFORE for an insert) — only that AFTER itself is populated.</li>
 * </ul>
 */
public enum ChangeExpectation {
    MUST_CHANGE,
    SYSTEM_CHANGE,
    MUST_NOT_CHANGE,
    INSERT,
    DELETE,
    RECREATED,
    IGNORE,
    MANDATORY
}
