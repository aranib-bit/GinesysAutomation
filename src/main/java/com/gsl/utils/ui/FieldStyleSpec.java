package com.gsl.utils.ui;

/**
 * The reusable style ROLES of the UI-contract checks (label, input, button, grid header, ...) —
 * KEYS only. The expected font-size/family/weight and text/background/border colours of each role
 * are NOT hardcoded here any more: they live in the baseline workbook
 * ({@code exceldata.properties → uiStyleBaselinePath}, {@code UiStyleBaseline}), sheet {@code Common}
 * for all vouchers plus an optional per-voucher sheet ({@code GV}/{@code AR}/{@code AP}) for what
 * genuinely differs. Changing an expected value is an Excel edit, not a code change.
 *
 * <p>Adding a role = add a constant here and a {@code Spec} row for it in the {@code Common} sheet
 * (a role with no row anywhere is reported as a visible SKIP "no baseline in Excel", never a silent
 * pass). Domain-agnostic by design: one role serves GV/AR/AP; per-voucher differences are rows in
 * that voucher's sheet, not new roles.
 */
public enum FieldStyleSpec {

    /** Any field label span (e.g. "Owner Site:", "Cash/Bank:", "Date:", "Remarks:"). */
    FIELD_LABEL,

    /** The required-field marker (`*`) appended to a mandatory field's label. */
    REQUIRED_MARKER,

    /** A plain editable text input (e.g. Reference, Remarks). Background is the EDITABLE-state one;
     *  for a DISABLED field the {@link #DISABLED_STATE} background applies instead. */
    TEXT_INPUT,

    /** A lookup/picklist input (e.g. Owner Site, Cash/Bank). */
    LOOKUP_INPUT,

    /** A date-picker input (e.g. Date). */
    DATE_INPUT,

    /** A read-only computed/display amount (e.g. Balance, the Allocate Sites Tagging Summary amounts). */
    READONLY_AMOUNT,

    /** The bold "value" half of a {@code gsl_FieldLabel} pair (e.g. Net Amount's "22.00 Cr (Payment)"). */
    AMOUNT_VALUE_LABEL,

    /** The plain "label" half of a {@code gsl_FieldLabel} pair (e.g. "Net Amount:"). */
    PAIRED_AMOUNT_LABEL,

    /** A section header (e.g. "Document Information", "Expense/Income Detail"). */
    SECTION_HEADER,

    /** A grid/table column header text. */
    GRID_HEADER,

    /** A plain text grid/table body cell. */
    GRID_CELL_TEXT,

    /** A numeric grid/table body cell. */
    GRID_CELL_NUMERIC,

    /** A status badge/indicator (e.g. "UN-POSTED", "RELEASED"/"UNRELEASED"). */
    STATUS_BADGE,

    /** A form/footer action button (e.g. Save). */
    BUTTON,

    /** The small in-grid row buttons ("Site", "Cheque/RTGS"). */
    GRID_ROW_BUTTON,

    /** The light footer buttons (Close; Copy has its own override in the GV sheet). */
    FOOTER_LIGHT_BUTTON,

    /** The Allocate Sites popup's Add button, resting state (its colours change on hover). */
    POPUP_ADD_BUTTON,

    /** Not a role of its own: its {@code BackgroundColor} is the background expected of ANY field whose
     *  expected state is DISABLED, replacing the role's own (EDITABLE-state) background. */
    DISABLED_STATE
}
