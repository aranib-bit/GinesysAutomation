package com.gsl.validation.snapshot;

/**
 * One failed check surfaced by {@link DiffEngine} — doc §29/§32, plan §1.
 *
 * @param table        the table the failing column/row belongs to
 * @param businessKey  display string of the row's business key, e.g. {@code "vchcode=48213"}
 * @param column       the failing column name ({@code null} for a row-level INSERT/DELETE failure)
 * @param expected     human-readable expected value/condition
 * @param actual       human-readable actual value
 * @param message      full assertion-style message (mirrors the codebase's existing
 *                     {@code SoftAssertions}/{@code Assert} message conventions)
 * @param severity     {@link Severity#CRITICAL} for header-identity/business-key-shape breaks,
 *                     {@link Severity#ERROR} for a plain column-value mismatch
 */
public record ValidationFailure(
        String table,
        String businessKey,
        String column,
        String expected,
        String actual,
        String message,
        Severity severity
) {
    public enum Severity { CRITICAL, ERROR }
}
