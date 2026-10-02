package com.gsl.enums;

/**
 * CRUD-style operation a test exercises against a sub-module — declared via
 * {@code FrameworkAnnotation#operation()} and consumed by {@code ExtentReport} to shard reports
 * per module/sub-module/operation (each combination gets its own report file, named and titled
 * using {@link #name()}/{@link #getDisplayName()}) and by {@code SummaryReportBuilder} to label
 * summary rows. {@link #NONE} is the sentinel default for a sub-module/test that isn't tied to a
 * specific operation (e.g. a read-only setup page).
 */
public enum AppOperation {

    ADD         ("Add"),
    MODIFY      ("Edit"),
    DELETE      ("Delete"),
    RELEASE     ("Release"),
    REVERT      ("Revert"),
    PRINT       ("Print"),
    AUTORELEASE ("Auto Release"),

    WORKBENCH   ("Workbench"),

    /** Quick set-off / document-adjustment flow (Finance › Journals › Document Adjustment). */
    QUICKSETOFF ("Quick Set-off"),

    EDITGRP     ("Edit Group"),
    EDITLEGGRP  ("Edit Ledger Group"),
    EDITLEGSEQ  ("Edit Ledger Sequence"),

    /** Sentinel for "no specific operation" — {@code ExtentReport}/{@code SummaryReportBuilder} skip the operation suffix for this constant. */
    NONE        ("—");

    private final String displayName;

    AppOperation(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label for report titles/summary rows (e.g. {@code MODIFY} → "Edit"). */
    public String getDisplayName() {
        return displayName;
    }
}
