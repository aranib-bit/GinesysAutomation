package com.gsl.validation.snapshot;

/**
 * How {@link DiffEngine} pairs up BEFORE/AFTER rows within one table — doc §13/§36.
 */
public enum RowMatchStrategy {

    /** Match rows whose {@link TableRule#getBusinessKeyColumns()} values are identical
     *  before/after — the normal case (a row is updated in place). */
    BY_KEY,

    /**
     * Match rows by business identity even when the underlying key column(s) may have been
     * delete+reinserted with a new surrogate id — reserved for tables where the app recreates
     * the row rather than updating it in place. The plan's original open item here — "confirm
     * empirically on first live run whether {@code finvchdet}/{@code finpost} rows are updated in
     * place or delete+reinserted" — is now moot rather than resolved-by-observation: both tables'
     * declared business keys ({@code finvchdet}: {@code [vchcode, glcode, slcode]}; {@code
     * finpost}: {@code [entcode, glcode, slcode, enttype]}) are built entirely from stable
     * business columns, never a recreatable surrogate id ({@code finpost.postcode} is
     * deliberately excluded from its key for exactly this reason) — so {@code BY_KEY} matches
     * correctly regardless of whether the underlying row is ever updated in place or recreated.
     * No table in the persistence map currently uses this constant — it stays reserved for a
     * future table whose business key genuinely can't be built from stable columns alone, without
     * changing {@link DiffEngine}'s API when that need arises.
     */
    BY_BUSINESS_IDENTITY_RECREATED
}
