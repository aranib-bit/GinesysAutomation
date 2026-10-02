package com.gsl.validation.ui;

/**
 * How deep a UI-contract check goes for a given page/run — mirrors the doc's "Level 1 UI Smoke" /
 * "Level 2 UI Contract" split (visual regression, its Level 3, is out of scope for this framework).
 */
public enum UiValidationLevel {
    /** Fast checks only: visibility, text, required-marker presence, enabled/disabled, basic state. */
    SMOKE,
    /** Full contract: everything SMOKE checks, plus font/color/background/border/dimension style. */
    CONTRACT,
}
