package com.gsl.enums;

public enum AppOperation {

    ADD         ("Add"),
    MODIFY      ("Edit"),
    DELETE      ("Delete"),
    RELEASE     ("Release"),
    REVERT      ("Revert"),
    PRINT       ("Print"),
    AUTORELEASE ("Auto Release"),

    WORKBENCH   ("Workbench"),

    QUICKSETOFF ("Quick Set-off"),

    EDITGRP     ("Edit Group"),
    EDITLEGGRP  ("Edit Ledger Group"),
    EDITLEGSEQ  ("Edit Ledger Sequence"),

    NONE        ("—");

    private final String displayName;

    AppOperation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
