package com.gsl.utils.ui;

/**
 * The visual/interaction state a field is expected to be in when a UI-contract check runs.
 *
 * <p>Ginesys General Voucher has no separate "View" page — {@code GeneralVoucherAdd} serves both
 * Add and Edit, and a Posted (immutable) voucher renders the exact same form with its fields turned
 * read-only rather than through a distinct View screen. {@code READONLY} is how "View Mode" UI
 * validation is expressed here: the same component validators run, just checking read-only visual
 * treatment instead of editable treatment, rather than a fictitious View page class being modeled.
 */
public enum UiFieldState {
    /** A field the user can currently type into / change via its picker. */
    EDITABLE,
    /** A field rendered read-only (e.g. {@code aria-readonly="true"}) — same as a Posted/View voucher. */
    READONLY,
    /** A field rendered disabled (e.g. {@code aria-disabled="true"}), distinct from read-only. */
    DISABLED,
}
